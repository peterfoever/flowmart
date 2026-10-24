package com.flowmart.product.dto;

import java.util.List;

public record InvalidGroup(
        String importItemKey,
        String importItemKeyNormalized,
        int rowFrom,
        int rowTo,
        int rowCount,
        String errorCode,
        String errorMessage,
        List<ImportRow> rows
) {
    public InvalidGroup {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
