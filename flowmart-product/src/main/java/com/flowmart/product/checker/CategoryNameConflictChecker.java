package com.flowmart.product.checker;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CategoryNameConflictChecker implements CategoryDeleteChecker {

    @Override
    public void check(CategoryDeleteContext context) {
        if (context.isDeleteChildren()) {
            return;
        }

        List<ProductCategory> directChildren = context.getDirectChildren() == null
                ? Collections.emptyList() : context.getDirectChildren();
        if (directChildren.isEmpty()) {
            return;
        }

        List<ProductCategory> targetParentChildren = context.getTargetParentChildren() == null
                ? Collections.emptyList() : context.getTargetParentChildren();
        Set<String> existingNames = targetParentChildren.stream()
                // 当前类目属于原父类目的子列表，但即将删除，不能算作上提冲突。
                .filter(category -> !Objects.equals(category.getId(), context.getCategory().getId()))
                .map(ProductCategory::getName)
                .collect(Collectors.toSet());
        Set<String> conflictNames = directChildren.stream()
                .map(ProductCategory::getName)
                .filter(existingNames::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!conflictNames.isEmpty()) {
            throw new BizException(
                    ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE,
                    "子类目上提后名称与目标父类目下已有类目冲突：" + String.join("、", conflictNames)
            );
        }
    }
}
