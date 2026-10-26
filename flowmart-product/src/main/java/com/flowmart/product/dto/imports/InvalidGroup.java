package com.flowmart.product.dto.imports;

import java.util.List;

/** 失败组：rows 为成功解析的连带失败行，parseErrors 为保留具体原因的解析失败行。 */
public record InvalidGroup(
        String importItemKey,
        String importItemKeyNormalized,
        int rowFrom,
        int rowTo,
        int rowCount,
        String errorCode,
        String errorMessage,
        List<ImportRow> rows,
        List<RowError> parseErrors
) {
    public InvalidGroup {
        rows = rows == null ? List.of() : List.copyOf(rows);
        parseErrors = parseErrors == null
                ? List.of()
                : List.copyOf(parseErrors);
    }
}
