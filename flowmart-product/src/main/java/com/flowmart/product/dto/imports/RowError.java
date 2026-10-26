package com.flowmart.product.dto.imports;

/** 一行一个代表错误；合法标识即使其他字段失败也必须保留规范化值。 */
public record RowError(
        int rowNum,
        String importItemKey,
        String importItemKeyNormalized,
        String field,
        String errorCode,
        String errorMessage
) {
}
