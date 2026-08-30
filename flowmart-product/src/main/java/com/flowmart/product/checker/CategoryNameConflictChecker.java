package com.flowmart.product.checker;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoryNameConflictChecker implements CategoryDeleteChecker {

    public CategoryNameConflictChecker() {
    }

    @Override
    public void check(CategoryDeleteContext context) {
        // 1. 级联删除模式 → 直接通过
        if (context.isDeleteChildren()) {
            return;
        }
        // 2. 无直接子类目 → 直接通过
        if (Objects.isNull(context.getDirectChildren()) || context.getDirectChildren().isEmpty()) {
            return;
        }


        // 3. 同名校验
        for (ProductCategory directChild : context.getDirectChildren()) {
            for (ProductCategory targetParentChild : context.getTargetParentChildren()) {
                if(Objects.equals(directChild.getName(), targetParentChild.getName())) {
                    throw new BizException(
                            ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE,
                            "子类目上提后名称与目标父类目下已有类目冲突：" + directChild.getName()
                    );
                }
            }

        }
    }
}
