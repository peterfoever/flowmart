package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入执行记录，不继承带逻辑删除/版本字段的 BaseEntity。
 * Writer 显式生成雪花 ID，并用 operatorId 填充 createdBy/updatedBy；
 * ownerId 是防重归属，不能用操作人替代。
 */
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
    /** 原始 JSON 数组文本，创建批次时显式写入 "[]"；不要再用 JSON TypeHandler 二次编码。 */
    private String warnings;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
