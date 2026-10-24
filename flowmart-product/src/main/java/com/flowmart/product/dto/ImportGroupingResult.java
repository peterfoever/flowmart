package com.flowmart.product.dto;

import java.util.List;

public record ImportGroupingResult(
        List<ImportGroup> groups,
        List<UngroupedRow> ungroupedRows,
        List<InvalidGroup> invalidGroups
) {
    public ImportGroupingResult {
        groups = groups == null ? List.of() : List.copyOf(groups);
        ungroupedRows = ungroupedRows == null ? List.of() : List.copyOf(ungroupedRows);
        invalidGroups = invalidGroups == null ? List.of() : List.copyOf(invalidGroups);
    }
}
