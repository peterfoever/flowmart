package com.flowmart.product.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ImportRow(
        int rowNum,                 // Excel 真实行号（从 2 开始）
        String importItemKey,       // 原始标识
        String spuName,
        Long categoryId,
        Long brandId,
        String mainImage,
        List<SpecDTO> specDefinition,
        List<SkuSpecValueDTO> specCombination,
        BigDecimal price,
        String skuImage
) {
        public ImportRow {
            // 输入行保留非法项供分组校验报告；不能因 copyOf 遇到 null 项就中断整份解析。
            specDefinition = specDefinition == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(specDefinition));
            specCombination = specCombination == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(specCombination));
        }
}
