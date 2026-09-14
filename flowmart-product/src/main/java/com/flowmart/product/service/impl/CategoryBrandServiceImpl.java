package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.BrandConverter;
import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.entity.ProductCategoryBrand;
import com.flowmart.product.enums.BrandStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.CategoryBrandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;


@Slf4j
@Service
public class CategoryBrandServiceImpl implements CategoryBrandService {
    @Autowired
    private ProductBrandMapper brandMapper;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private BrandConverter converter;
    /**
     * 系统操作人 ID，后续接入登录上下文后替换
     */
    private static final Long SYSTEM_OPERATOR_ID = 0L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceCategoryBrands(Long categoryId, ReplaceCategoryBrandsDTO request) {
        Long operatorId = SYSTEM_OPERATOR_ID;

        // Step 0: 锁定类目行
        ProductCategory category = categoryMapper.selectByIdForUpdate(categoryId);
        if (category == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        // Step 1: 校验叶子类目
        if (!categoryMapper.isLeafCategory(categoryId)) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_LEAF);
        }
        // Step 2: 参数校验 + 去重
        List<Long> brandIds = request.getBrandIds();
        List<Long> distinctIds = brandIds.stream().distinct().collect(toList());
        // Step 3: 批量校验品牌
        if (!distinctIds.isEmpty()) {
            List<ProductBrand> brands = brandMapper.selectByIdsAndDeleted(distinctIds, 0L);
            if (distinctIds.size() != brandIds.size()) {
                // 找出缺失的 ID
                Set<Long> foundIds = brands.stream().map(ProductBrand::getId).collect(Collectors.toSet());
                List<Long> missingIds = distinctIds.stream().filter(id -> !foundIds.contains(id)).collect(toList());
                throw new BizException(ProductErrorCode.BRAND_NOT_FOUND,
                        "品牌不存在: " + missingIds);
            }
            for (ProductBrand brand : brands) {
                if (BrandStatus.DISABLED.equals(brand.getStatus())) {
                    throw new BizException(ProductErrorCode.BRAND_DISABLED,
                            "品牌已禁用: " + brand.getName());
                }
            }

        }
        // Step 4: 逻辑删除旧绑定（返回 0 是正常情况）
        int deletedCount = brandMapper.logicDeleteByCategoryId(categoryId, operatorId);
        log.debug("删除旧绑定: categoryId={}, affected={}", categoryId, deletedCount);
        // Step 5: 批量插入新绑定（空列表跳过）
        if (!distinctIds.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<ProductCategoryBrand> bindings = distinctIds.stream().map(brandId -> {
                ProductCategoryBrand binding =
                new ProductCategoryBrand();
                binding.setId(SnowflakeIdUtil.nextId());
                binding.setCategoryId(categoryId);
                binding.setBrandId(brandId);
                binding.setCreatedBy(operatorId);
                binding.setCreatedAt(now);
                binding.setUpdatedBy(operatorId);
                binding.setUpdatedAt(now);
                binding.setDeleted(0L);
                return binding;
            }).collect(Collectors.toList());
        }
        int insertedCount = brandMapper.batchInsert(bindings);
        if (insertedCount != bindings.size()) {
            throw new BizException(ProductErrorCode.CATEGORY_BRAND_REPLACE_FAILED,
                    String.format("期望插入 %d 条绑定，实际插入 %d 条",
                            bindings.size(), insertedCount));
        }
    } else {
        log.debug("brandIds 为空，跳过插入，仅解除绑定");
    }

    // Step 6: 日志
    log.info("替换类目品牌绑定执行完成: categoryId={}, 旧绑定删除数={}, 新绑定数={}",
    categoryId, deletedCount, distinctIds.size());
    }
}
