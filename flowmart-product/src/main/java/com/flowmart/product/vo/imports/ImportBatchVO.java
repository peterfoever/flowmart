package com.flowmart.product.vo.imports;

import java.time.LocalDateTime;
import java.util.List;

/** 不暴露服务器文件路径、请求幂等键和内部指纹。 */
public record ImportBatchVO(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        Long batchId, String fileName, String status,
        Integer totalSpuCount, Integer successSpuCount, Integer failedSpuCount,
        Integer unprocessedSpuCount, Integer totalRowCount, Integer successRowCount,
        Integer failedRowCount, Integer ungroupedFailedRowCount, String errorSummary,
        List<String> warnings, LocalDateTime startedAt, LocalDateTime finishedAt) {}
