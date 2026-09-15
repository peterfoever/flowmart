package com.flowmart.product.vo;

import lombok.Data;

import java.util.List;

@Data
public class CategoryBrandVO {
    private List<Long> ids;
    private String name;
    private String logoUrl;
    private String initial;
    private Integer sortNo;
}
