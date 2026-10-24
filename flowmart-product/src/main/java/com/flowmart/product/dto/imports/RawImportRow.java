package com.flowmart.product.dto.imports;

import java.util.Map;

/** Excel原始行；列索引从0开始，真实行号由Validator统一校验，不因跳过空行重新编号。 */
public record RawImportRow(int rowNum, Map<Integer, RawCell> cells) {
    public RawImportRow {
        cells = cells == null ? Map.of() : Map.copyOf(cells);
        if (cells.keySet().stream().anyMatch(column -> column < 0)) {
            throw new IllegalArgumentException("列索引不能小于0");
        }
    }

    /** 未出现的单元格按空白处理；显式null值由构造时拒绝。 */
    public RawCell cell(int column) {
        return cells.getOrDefault(column, RawCell.blank());
    }

    public boolean allBlank() {
        return cells.values().stream().allMatch(RawCell::isBlank);
    }
}
