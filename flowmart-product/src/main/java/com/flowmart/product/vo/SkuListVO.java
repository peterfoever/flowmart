package com.flowmart.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SkuListVO {
    private Long id;
    private Long spuId;
    private String skuCode;
    private Map<String, String> specValues;
    private String specText;          // 前端展示，例如 "红色 / XL"
    private BigDecimal price;
    private BigDecimal marketPrice;
    private Integer stock;
    private Boolean isDefault;
    private Integer status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
