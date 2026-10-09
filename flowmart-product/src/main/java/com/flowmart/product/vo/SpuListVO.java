package com.flowmart.product.vo;



import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SpuListVO {
    private Long id;

    private String name;

    private String spuCode;

    private Long categoryId;

    private Long brandId;

    private String categoryName;

    private String categoryText;

    private String brandName;

    private String brandText;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
