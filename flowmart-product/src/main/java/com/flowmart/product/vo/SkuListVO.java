package com.flowmart.product.vo;

import lombok.Data;
import com.flowmart.product.dto.SkuSpecValueDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SkuListVO {
    private Long id;
    private Long spuId;
    private String skuCode;
    private List<SkuSpecValueDTO> specValues;
    private String specText;          // 前端展示，例如 "红色 / XL"
    private BigDecimal price;
    private String imageUrl;
    private Boolean isDefault;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
