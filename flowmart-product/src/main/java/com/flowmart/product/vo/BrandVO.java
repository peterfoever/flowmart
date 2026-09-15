package com.flowmart.product.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandVO {
    private Long id;
    private String name;
    private String logoUrl;
    private String initial;
    private Integer sortNo;
    private Integer status;
    private String statusText;   // 派生字段，由枚举转换
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
