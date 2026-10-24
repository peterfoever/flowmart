package com.flowmart.product.dto;

public record UngroupedRow(
        int rowNum,
        String importItemKey,
        String errorMessage
) {
}
