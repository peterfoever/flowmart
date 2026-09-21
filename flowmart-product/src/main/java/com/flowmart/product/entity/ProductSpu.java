package com.flowmart.product.entity;

import com.flowmart.product.dto.SpecDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductSpu {
    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private Long brandId;
    private String mainImageUrl;
    private List<String> carouselImages;          // JSON ↔ List
    private List<SpecDTO> specs;                  // JSON ↔ List<SpecDTO>
    private String description;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Long deleted;
    private Integer version;
}
