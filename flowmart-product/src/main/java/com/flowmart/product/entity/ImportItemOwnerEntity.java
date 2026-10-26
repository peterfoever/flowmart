package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跨文件防重凭证，不随商品逻辑删除而失效。
 * 事务内允许占位；提交前必须回填商品和分组结果，失败时随组事务回滚。
 * ID、操作人由 Writer 显式填充。
 */
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
