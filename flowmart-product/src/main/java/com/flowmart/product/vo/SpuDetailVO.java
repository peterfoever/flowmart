package com.flowmart.product.vo;

import com.flowmart.product.dto.SpecDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SpuDetailVO {
    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String mainImageUrl;
    private List<String> carouselImages;
    private List<SpecDTO> specs;
    private String description;
    private Integer status;
    private String statusText;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
