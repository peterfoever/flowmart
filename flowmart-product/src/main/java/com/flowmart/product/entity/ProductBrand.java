package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowmart.common.mybatis.BaseEntity;
import lombok.Data;

@Data
@TableName("product_brand")
public class ProductBrand extends BaseEntity {
    /** 主键，雪花 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 品牌名称
     */
    @TableField("name")
    private String name;

    /**
     * logo
     */
    @TableField("logo_url")
    private String logoUrl;

    /**
     * 品牌首字母
     */
    @TableField("initial")
    private String initial;

    /**
     * 排序值，越小越靠前
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 状态 1启用 0禁用
     */
    @TableField("status")
    private Integer status;

}
