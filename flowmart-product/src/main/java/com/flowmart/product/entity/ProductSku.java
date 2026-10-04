package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flowmart.common.mybatis.BaseEntity;
import com.flowmart.product.dto.SkuSpecValueDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@TableName(value = "product_sku", autoResultMap = true)
public class ProductSku extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("sku_code")
    private String skuCode;

    @TableField("spu_id")
    private Long spuId;

    @TableField(value = "spec_values", typeHandler = JacksonTypeHandler.class)
    private List<SkuSpecValueDTO> specValues;

    @TableField("spec_hash")
    private String specHash;

    @TableField("price")
    private BigDecimal price;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("image_url")
    private String imageUrl;
}
