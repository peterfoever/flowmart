package com.flowmart.product.vo;

import lombok.Data;
import com.flowmart.product.dto.SkuSpecValueDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SkuDetailVO {
    private Long id;
    private Long spuId;
    private String skuCode;
    private List<SkuSpecValueDTO> specValues;
    private String specText;
    private BigDecimal price;
    private String imageUrl;
    private Boolean isDefault;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
