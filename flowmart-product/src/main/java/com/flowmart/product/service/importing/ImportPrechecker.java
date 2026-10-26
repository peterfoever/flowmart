package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.product.dto.imports.ImportGroup;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.*;
import com.flowmart.product.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 事务外批量预检，仅提前拒绝；预检通过后Writer仍锁内复核。 */
@Component
@RequiredArgsConstructor
public class ImportPrechecker {
    private final ProductCategoryMapper categories;
    private final ProductBrandMapper brands;
    private final ImportItemOwnerMapper owners;
    private final ImportReferenceMapper references;
    public Map<String, ImportGroupRejectedException> check(Long owner, List<ImportGroup> groups) {
        Map<String, ImportGroupRejectedException> rejected = new HashMap<>();
        for (int start = 0; start < groups.size(); start += 200) {
            var part = groups.subList(start, Math.min(start + 200, groups.size()));
            var categoryIds = part.stream().map(ImportGroup::categoryId).distinct().toList();
            var brandIds = part.stream().map(ImportGroup::brandId).filter(Objects::nonNull).distinct().toList();
            Set<String> bindings = brandIds.isEmpty() ? Set.of() : references.bindings(categoryIds, brandIds).stream()
                    .map(binding -> binding.getCategoryId() + ":" + binding.getBrandId()).collect(Collectors.toSet());
            var categoryMap = categories.selectBatchIds(categoryIds).stream().collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
            Map<Long, ProductBrand> brandMap = brandIds.isEmpty() ? Map.of() : brands.selectByIdsAndDeleted(brandIds, 0L).stream()
                    .collect(Collectors.toMap(ProductBrand::getId, Function.identity()));
            Set<Long> nonleaf = categories.selectList(Wrappers.<ProductCategory>lambdaQuery()
                    .select(ProductCategory::getParentId).in(ProductCategory::getParentId, categoryIds))
                    .stream().map(ProductCategory::getParentId).collect(Collectors.toSet());
            Set<String> existing = owners.selectList(Wrappers.<ImportItemOwnerEntity>lambdaQuery()
                    .select(ImportItemOwnerEntity::getImportItemKeyNormalized).eq(ImportItemOwnerEntity::getOwnerId, owner)
                    .in(ImportItemOwnerEntity::getImportItemKeyNormalized, part.stream().map(ImportGroup::importItemKeyNormalized).toList()))
                    .stream().map(ImportItemOwnerEntity::getImportItemKeyNormalized).collect(Collectors.toSet());
            for (var group : part) {
                ProductCategory category = categoryMap.get(group.categoryId());
                ProductBrand brand = group.brandId() == null ? null : brandMap.get(group.brandId());
                ProductErrorCode code = null;
                String field = "categoryId";
                if (category == null) code = ProductErrorCode.CATEGORY_NOT_FOUND;
                else if (!CategoryStatus.ENABLED.matches(category.getStatus())) code = ProductErrorCode.CATEGORY_DISABLED;
                else if (nonleaf.contains(category.getId())) code = ProductErrorCode.CATEGORY_NOT_LEAF;
                else if (group.brandId() != null && brand == null) { code = ProductErrorCode.BRAND_NOT_FOUND; field = "brandId"; }
                else if (brand != null && !BrandStatus.ENABLED.matches(brand.getStatus())) { code = ProductErrorCode.BRAND_DISABLED; field = "brandId"; }
                else if (brand != null && !bindings.contains(group.categoryId() + ":" + group.brandId())) { code = ProductErrorCode.CATEGORY_BRAND_NOT_BOUND; field = "brandId"; }
                else if (existing.contains(group.importItemKeyNormalized())) { code = ProductErrorCode.IMPORT_ITEM_DUPLICATED; field = "importItemKey"; }
                if (code != null) rejected.put(group.importItemKeyNormalized(), new ImportGroupRejectedException(code, field));
            }
        }
        return rejected;
    }
}
