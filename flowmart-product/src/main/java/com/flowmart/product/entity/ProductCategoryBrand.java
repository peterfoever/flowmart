package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowmart.common.mybatis.BaseEntity;
import lombok.Data;

@Data
@TableName("product_category_brand")
public class ProductCategoryBrand extends BaseEntity {
    /** 主键，雪花 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 类目id
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 品牌id
     */
    @TableField("brand_id")
    private Long brandId;
}
