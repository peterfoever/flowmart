package com.flowmart.product.vo;

import lombok.Data;



@Data
public class CategoryBrandVO {
    private Long id;
    private String name;
    private String logoUrl;
    private String initial;
    private Integer sortNo;
    private Integer status;

}
