package com.flowmart.product.checker;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductSpuMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategorySpuReferenceChecker implements CategoryDeleteChecker{

    private final ProductSpuMapper spuMapper;

    public CategorySpuReferenceChecker(ProductSpuMapper spuMapper) {
        this.spuMapper = spuMapper;
    }

    @Override
    public void check(CategoryDeleteContext context) {
        List<Long> categoryIds;
        if(context.isDeleteChildren()) {
            // 级联删除：检查当前类目 + 全部后代
            categoryIds = context.getSubtree().stream()
                    .map(ProductCategory::getId)
                    .collect(Collectors.toList());

        }else {
            // 非级联删除：只检查当前类目
            categoryIds = Collections.singletonList(context.getCategory().getId());
        }
        if (categoryIds.isEmpty()) {
            return;
        }
        if(spuMapper.existsByCategoryIdsAndDeleted(categoryIds)) {
            throw new BizException(ProductErrorCode.CATEGORY_IN_USE_BY_SPU);
        }
    }
}
