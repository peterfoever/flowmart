package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "import_batch", autoResultMap = true)
public class ImportBatchEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long ownerId;
    private Long operatorId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String fileName;
    private String fileHash;
    private String fileUrl;

    private Integer totalSpuCount;
    private Integer successSpuCount;
    private Integer failedSpuCount;
    private Integer ungroupedFailedRowCount;
    private Integer unprocessedSpuCount;
    private Integer totalRowCount;
    private Integer successRowCount;
    private Integer failedRowCount;

    private String status;
    private String errorSummary;
    private String warnings;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
