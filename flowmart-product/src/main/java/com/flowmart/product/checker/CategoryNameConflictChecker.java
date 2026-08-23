package com.flowmart.product.checker;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CategoryNameConflictChecker implements CategoryDeleteChecker {
    private CategoryDeleteContext categoryDeleteContext;

    public CategoryNameConflictChecker(CategoryDeleteContext categoryDeleteContext) {
    }

    @Override
    public void check(CategoryDeleteContext context) {
        // 1. 级联删除模式 → 直接通过
        if (categoryDeleteContext.isDeleteChildren()) {
            return;
        }
        // 2. 无直接子类目 → 直接通过
        if (categoryDeleteContext.getDirectChildren() == null || categoryDeleteContext.getDirectChildren().isEmpty()) {
            return;
        }
        // 3. 当前类目是一级类目（parentId == 0）→ 上提到一级，不检查名称冲突
        if (categoryDeleteContext.getCategory().getParentId() == 0) {
            return;
        }

        // 4. 同名校验
        for (ProductCategory directChild : categoryDeleteContext.getDirectChildren()) {
            if (categoryDeleteContext.getCategory().getName() == directChild.getName()) {
                throw new BizException(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE);
            }

        }
    }
}
