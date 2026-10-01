package com.flowmart.product.dto;

import lombok.Data;

/** 一个规格维度的选中值；区别于 SpecDTO 中的多个候选值。由后端生成。 */
@Data
public class SkuSpecValueDTO {
    private String name;
    private String value;
}
