package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "import_item_owner", autoResultMap = true)
public class ImportItemOwnerEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long ownerId;
    private String importItemKey;
    private String importItemKeyNormalized;
    private Long batchId;
    private Long groupResultId;
    private Long spuId;
    private String spuCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
