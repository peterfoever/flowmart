package com.flowmart.common.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：审计字段 + 逻辑删除 + 乐观锁。
 * <p>
 * 所有业务表都必须带这几列。为什么：
 * <ul>
 *   <li>审计字段 —— 线上出问题时第一件事就是查「谁在什么时候改了这条数据」。</li>
 *   <li>逻辑删除 —— 业务数据几乎不允许物理删除，删错了要能捞回来，也要保留对账链路。</li>
 *   <li>乐观锁 version —— 并发更新时防止后写覆盖先写（lost update）。</li>
 * </ul>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 0 未删除，非 0 已删除（存删除时间戳，保证唯一索引在软删后仍可复用） */
    @TableLogic(value = "0", delval = "UNIX_TIMESTAMP()")
    @TableField("deleted")
    private Long deleted;

    @Version
    @TableField("version")
    private Integer version;
}
