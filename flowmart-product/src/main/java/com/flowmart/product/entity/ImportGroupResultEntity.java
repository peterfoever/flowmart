package com.flowmart.product.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "import_group_result", autoResultMap = true)
public class ImportGroupResultEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long batchId;
    private String importItemKey;
    private String importItemKeyNormalized;
    private String spuName;
    private Integer rowCount;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long spuId;
    private String spuCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
