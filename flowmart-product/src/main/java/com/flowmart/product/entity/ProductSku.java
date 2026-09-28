package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName(value = "product_spu", autoResultMap = true)
public class ProductSku {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("sku_code")
    private String skuCode;

    @TableField("spu_code")
    private String spuCode;

    @TableField("category_id")
    private Long categoryId;

    @TableField("brand_id")
    private Long brandId;

    @TableField("price")
    private BigDecimal price;

    @TableField("name")
    private String name;

    @TableField("image_url")
    private String imageUrl;
}
