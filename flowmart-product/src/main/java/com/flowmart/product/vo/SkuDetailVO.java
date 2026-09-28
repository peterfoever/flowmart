package com.flowmart.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SkuDetailVO {
    private Long id;
    private Long spuId;
    private String skuCode;
    private Map<String, String> specValues;
    private String specHash;
    private String specText;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private Integer stock;
    private Boolean isDefault;
    private Integer status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
