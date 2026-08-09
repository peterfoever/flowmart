package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.flowmart.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("product_category")
@EqualsAndHashCode(callSuper = true)

public class ProductCategory extends BaseEntity {

    /** 主键，雪花 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 父类目 ID，一级类目为 0
     */
    @TableField("parent_id")
    private Long parentId;
    /**
     * 类目名称
     */
    @TableField("name")
    private String name;
    /**
     * 类目图标
     */
    @TableField("icon_url")
    private String iconUrl;
    /**
     * 层级，1 起
     */
    @TableField("level")
    private Integer level;
    /**
     * 同级排序，越小越靠前，默认 0
     */
    @TableField("sort_no")
    private Integer sortNo;
    /**
     * 状态 1启用 0禁用
     */
    @TableField("status")
    private Integer status;


}
