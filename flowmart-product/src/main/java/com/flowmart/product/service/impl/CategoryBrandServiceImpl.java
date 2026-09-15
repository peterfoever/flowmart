package com.flowmart.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.entity.ProductCategoryBrand;
import com.flowmart.product.enums.BrandStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.CategoryBrandService;
import com.flowmart.product.vo.CategoryBrandVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryBrandServiceImpl implements CategoryBrandService {
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private static final Long SYSTEM_OPERATOR_ID = 0L;
    private static final int BATCH_SIZE = 500;

    /** 同一类目的替换请求通过类目行锁串行执行，所有批次在同一事务中提交。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceCategoryBrands(Long categoryId, ReplaceCategoryBrandsDTO request) {
        if (categoryId == null || categoryId <= 0 || request == null || request.getBrandIds() == null
                || request.getBrandIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        // 跨批次按统一顺序请求品牌锁，降低重叠品牌集合产生死锁的风险。
        List<Long> distinctIds = request.getBrandIds().stream().distinct().sorted().toList();

        ProductCategory category = categoryMapper.selectByIdForUpdate(categoryId);
        if (category == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!categoryMapper.isLeafCategory(categoryId)) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_LEAF);
        }

        // 先完成全部品牌校验，再修改旧绑定，避免后面的校验失败产生无用写操作。
        for (int start = 0; start < distinctIds.size(); start += BATCH_SIZE) {
            List<Long> batch = distinctIds.subList(start, Math.min(start + BATCH_SIZE, distinctIds.size()));
            List<ProductBrand> brands = brandMapper.selectByIdsForUpdate(batch);
            Set<Long> foundIds = brands.stream().map(ProductBrand::getId).collect(Collectors.toSet());
            List<Long> missingIds = batch.stream().filter(id -> !foundIds.contains(id)).toList();
            if (!missingIds.isEmpty()) {
                throw new BizException(ProductErrorCode.BRAND_NOT_FOUND, "品牌不存在: " + missingIds);
            }
            for (ProductBrand brand : brands) {
                if (!BrandStatus.ENABLED.matches(brand.getStatus())) {
                    throw new BizException(ProductErrorCode.BRAND_DISABLED, "品牌未启用: " + brand.getName());
                }
            }
        }

        int deletedCount = brandMapper.logicDeleteByCategoryId(categoryId, SYSTEM_OPERATOR_ID);
        LocalDateTime now = LocalDateTime.now();
        for (int start = 0; start < distinctIds.size(); start += BATCH_SIZE) {
            List<Long> batch = distinctIds.subList(start, Math.min(start + BATCH_SIZE, distinctIds.size()));
            List<ProductCategoryBrand> bindings = new ArrayList<>(batch.size());
            for (Long brandId : batch) {
                ProductCategoryBrand binding = new ProductCategoryBrand();
                binding.setId(IdWorker.getId());
                binding.setCategoryId(categoryId);
                binding.setBrandId(brandId);
                binding.setCreatedBy(SYSTEM_OPERATOR_ID);
                binding.setCreatedAt(now);
                binding.setUpdatedBy(SYSTEM_OPERATOR_ID);
                binding.setUpdatedAt(now);
                binding.setDeleted(0L);
                binding.setVersion(0);
                bindings.add(binding);
            }
            int insertedCount = brandMapper.batchInsert(bindings);
            if (insertedCount != bindings.size()) {
                throw new BizException(ProductErrorCode.CATEGORY_BRAND_REPLACE_FAILED,
                        String.format("期望插入 %d 条绑定，实际插入 %d 条", bindings.size(), insertedCount));
            }
        }
        log.info("替换类目品牌绑定执行完成: categoryId={}, 旧绑定删除数={}, 新绑定数={}",
                categoryId, deletedCount, distinctIds.size());
    }

    @Override
    public List<CategoryBrandVO> getCategoryBrands(Long categoryId) {
        log.debug("查询类目绑定品牌: categoryId={}", categoryId);
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        List<CategoryBrandVO> categoryBrandVOS = brandMapper.selectBrandsByCategoryId(categoryId);
        if(CollectionUtils.isEmpty(categoryBrandVOS)) {
            log.debug("类目无绑定品牌: categoryId={}", categoryId);
            return List.of();
        }

        return categoryBrandVOS;
    }

}
