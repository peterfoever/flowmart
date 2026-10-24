package com.flowmart.product.dto.imports;

/** 标识无法规范化的行，不计入失败商品组数，保留原始错误定位信息。 */
public record UngroupedRow(
        int rowNum,
        String importItemKey,
        String field,
        String errorCode,
        String errorMessage
) {
}
