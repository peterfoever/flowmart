package com.flowmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flowmart.common.mybatis.BaseEntity;
import com.flowmart.product.dto.SpecDTO;
import lombok.Data;


import java.util.List;

@Data
@TableName(value = "product_spu", autoResultMap = true)
public class ProductSpu extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("spu_code")
    private String spuCode;

    @TableField("name")
    private String name;

    @TableField("category_id")
    private Long categoryId;

    @TableField("brand_id")
    private Long brandId;

    @TableField("main_image_url")
    private String mainImageUrl;
    @TableField(
            value = "carousel_images",
            typeHandler = JacksonTypeHandler.class
    )
    private List<String> carouselImages;

    @TableField(
            value = "spec_json",
            typeHandler = JacksonTypeHandler.class
    )
    private List<SpecDTO> specs;

    @TableField("description")// JSON ↔ List<SpecDTO>
    private String description;

    /**
     * 状态 1启用 0禁用
     */
    @TableField("status")
    private Integer status;

}
