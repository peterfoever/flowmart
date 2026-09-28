package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowmart.common.mybatis.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName(value = "product_sku", autoResultMap = true)
public class ProductSku extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("sku_code")
    private String skuCode;

    @TableField("spu_id")
    private String spuId;

    @TableField("spec_values")
    private Long specValues;

    @TableField("spec_hash")
    private Long specHash;

    @TableField("price")
    private BigDecimal price;

    @TableField("is_default")
    private boolean isDefault;

    @TableField("image_url")
    private String imageUrl;
}
