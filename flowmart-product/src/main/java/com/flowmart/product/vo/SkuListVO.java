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
    private String specText;          // 前端展示，例如 "颜色=红色 / 尺码=XL"；无规格为 "默认规格"
    private BigDecimal price;
    private String imageUrl;
    private Boolean isDefault;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
