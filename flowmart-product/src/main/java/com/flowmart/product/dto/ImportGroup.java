package com.flowmart.product.dto;

import java.util.List;

public record ImportGroup(
        String importItemKey,
        String importItemKeyNormalized,
        String spuName,
        Long categoryId,
        Long brandId,
        String mainImage,
        List<SpecDTO> specDefinition,
        List<ImportRow> rows
) {
    public ImportGroup {
        specDefinition = specDefinition == null ? List.of() : List.copyOf(specDefinition);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }
}
