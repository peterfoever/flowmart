package com.flowmart.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ImportRow(
        int rowNum,                 // Excel 真实行号（从 2 开始）
        String importItemKey,       // 原始标识
        String spuName,
        Long categoryId,
        Long brandId,
        String mainImage,
        List<SkuSpecValueDTO> specDefinition,
        List<SkuSpecValueDTO> specCombination,
        BigDecimal price,
        String skuImage
) {
        public ImportRow {
            specDefinition = specDefinition == null ? List.of() : List.copyOf(specDefinition);
            specCombination = specCombination == null ? List.of() : List.copyOf(specCombination);
        }
}
