package com.flowmart.product.dto.imports;

import java.util.List;

/**
 * 文件解析结果。文件级拒绝时不能携带部分解析结果，编排层必须终止而不是继续分组。
 * 行号和规范化标识的跨列表一致性由 ImportRowGrouper 校验。
 */
public record ImportParseResult(
        List<ImportRow> rows,
        List<RowError> rowErrors,
        FileLevelError fileLevelError
) {
    public ImportParseResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
        rowErrors = rowErrors == null ? List.of() : List.copyOf(rowErrors);
        if (fileLevelError != null && (!rows.isEmpty() || !rowErrors.isEmpty())) {
            throw new IllegalArgumentException("文件级拒绝不能携带成功行或行错误，请丢弃部分解析结果");
        }
    }

    public boolean hasFileLevelError() {
        return fileLevelError != null;
    }
}
