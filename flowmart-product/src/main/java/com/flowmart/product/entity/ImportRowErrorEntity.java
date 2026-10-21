package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "import_row_error", autoResultMap = true)
public class ImportRowErrorEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long batchId;
    private Long groupResultId;
    private Integer rowNum;
    private String importItemKey;
    private String field;
    private String errorCode;
    private String errorMessage;

    private LocalDateTime createdAt;
}
