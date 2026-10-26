package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.flowmart.common.mybatis.BaseEntity;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.convert.ImportConverter;
import com.flowmart.product.dto.imports.ImportGroup;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.*;
import com.flowmart.product.generator.*;
import com.flowmart.product.mapper.*;
import com.flowmart.product.support.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportGroupWriter {
    private final ImportBatchMapper batchMapper;
    private final ImportItemOwnerMapper ownerMapper;
    private final ImportGroupResultMapper resultMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final SpuCodeGenerator spuCodes;
    private final SkuCodeGenerator skuCodes;
    private final ImportConverter converter;

    /** 同一代理事务内完成来源占位、SPU、全部SKU、成功结果及来源回填。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(Long batchId, Long ownerId, Long actor, ImportGroup group) {
        if (!"RUNNING".equals(batchMapper.lockStatus(batchId, ownerId))) throw new IllegalStateException("批次不在执行中");
        // 不信任调用者绕过分组器构造的DTO，锁内复核规则；先锁类目再锁品牌，与已有服务一致。
        var regrouped = new ImportRowGrouper().group(group.rows());
        if (regrouped.groups().size() != 1 || !regrouped.invalidGroups().isEmpty()
                || !regrouped.ungroupedRows().isEmpty()) {
            throw new ImportGroupRejectedException(ProductErrorCode.GROUP_SPEC_DEFINITION_INVALID, "GROUP");
        }
        group = regrouped.groups().getFirst();
        ProductCategory category = categoryMapper.selectByIdForUpdate(group.categoryId());
        if (category == null) reject(ProductErrorCode.CATEGORY_NOT_FOUND, "categoryId");
        if (!CategoryStatus.ENABLED.matches(category.getStatus())) reject(ProductErrorCode.CATEGORY_DISABLED, "categoryId");
        if (!categoryMapper.isLeafCategory(group.categoryId())) reject(ProductErrorCode.CATEGORY_NOT_LEAF, "categoryId");
        if (group.brandId() != null) {
            ProductBrand brand = brandMapper.selectByIdForUpdate(group.brandId());
            if (brand == null) reject(ProductErrorCode.BRAND_NOT_FOUND, "brandId");
            if (!BrandStatus.ENABLED.matches(brand.getStatus())) reject(ProductErrorCode.BRAND_DISABLED, "brandId");
            if (!categoryMapper.existsByCategoryIdAndBrandId(group.categoryId(), group.brandId())) {
                reject(ProductErrorCode.CATEGORY_BRAND_NOT_BOUND, "brandId");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ImportItemOwnerEntity source = new ImportItemOwnerEntity();
        source.setId(IdWorker.getId()); source.setOwnerId(ownerId); source.setBatchId(batchId);
        source.setImportItemKey(ImportTexts.clip(cn.hutool.core.util.StrUtil.trim(group.importItemKey()), 64));
        source.setImportItemKeyNormalized(group.importItemKeyNormalized());
        source.setCreatedBy(actor); source.setUpdatedBy(actor); source.setCreatedAt(now); source.setUpdatedAt(now);
        try {
            requireOne(ownerMapper.insert(source));
        } catch (DuplicateKeyException e) {
            if (ImportSqlErrors.duplicateOn(e, "uk_import_owner_key")) reject(ProductErrorCode.IMPORT_ITEM_DUPLICATED, "importItemKey");
            throw e;
        }
        ProductSpu spu = converter.toSpu(group);
        spu.setId(IdWorker.getId()); spu.setSpuCode(spuCodes.next()); spu.setStatus(SpuStatus.DRAFT.getCode());
        spu.setCarouselImages(List.of()); audit(spu, actor, now);
        requireOne(spuMapper.insert(spu));
        List<ProductSku> skus = new ArrayList<>();
        for (var row : group.rows()) {
            ProductSku sku = converter.toSku(row);
            sku.setSpecValues(row.specCombination().stream().map(value -> {
                var clean = new com.flowmart.product.dto.SkuSpecValueDTO();
                clean.setName(cn.hutool.core.util.StrUtil.trim(value.getName()));
                clean.setValue(cn.hutool.core.util.StrUtil.trim(value.getValue()));
                return clean;
            }).toList());
            sku.setId(IdWorker.getId()); sku.setSpuId(spu.getId()); sku.setSkuCode(skuCodes.next());
            sku.setSpecHash(SpecHashCalculator.calculate(row.specCombination()));
            sku.setIsDefault(row.specCombination().isEmpty()); audit(sku, actor, now);
            skus.add(sku);
        }
        for (int i = 0; i < skus.size(); i += 200) {
            var part = skus.subList(i, Math.min(i + 200, skus.size()));
            if (skuMapper.batchInsert(part) != part.size()) throw new IllegalStateException("SKU批量写入行数异常");
        }
        ImportGroupResultEntity result = ImportTexts.result(batchId, actor, group.importItemKey(),
                group.importItemKeyNormalized(), group.spuName(), group.rowCount());
        result.setStatus("SUCCEEDED"); result.setSpuId(spu.getId()); result.setSpuCode(spu.getSpuCode());
        requireOne(resultMapper.insert(result));
        source.setSpuId(spu.getId()); source.setSpuCode(spu.getSpuCode()); source.setGroupResultId(result.getId());
        requireOne(ownerMapper.updateById(source));
        requireOne(batchMapper.heartbeat(batchId, LocalDateTime.now()));
    }

    private void reject(ProductErrorCode code, String field) { throw new ImportGroupRejectedException(code, field); }
    static void requireOne(int rows) { if (rows != 1) throw new IllegalStateException("导入写入影响行数异常"); }
    private void audit(BaseEntity entity, Long actor, LocalDateTime now) {
        entity.setCreatedBy(actor); entity.setUpdatedBy(actor); entity.setCreatedAt(now); entity.setUpdatedAt(now);
        entity.setDeleted(0L); entity.setVersion(0);
    }
}
