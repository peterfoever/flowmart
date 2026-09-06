package com.flowmart.product.checker;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.context.CategoryDeleteContext;

public interface CategoryDeleteChecker {
    /**
     * 校验删除操作是否允许执行
     *
     * @param context 删除上下文（只读）
     * @throws BizException 校验不通过时抛出，事务整体回滚
     */
    void check(CategoryDeleteContext context);
}
