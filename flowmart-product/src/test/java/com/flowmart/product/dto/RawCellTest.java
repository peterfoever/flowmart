package com.flowmart.product.dto;

import com.flowmart.product.dto.imports.RawCell;
import com.flowmart.product.dto.imports.RawImportRow;
import com.flowmart.product.enums.RawCellType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RawCellTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u3000"})
    @DisplayName("文本空值或半角全角空白判为空白")
    void blankText(String value) {
        assertTrue(RawCell.text(value).isBlank());
    }

    @Test
    @DisplayName("公式、错误、数字即使没有rawValue也不能被当成空白")
    void specialTypesAreNeverBlank() {
        assertFalse(RawCell.formula().isBlank());
        assertFalse(RawCell.error().isBlank());
        assertFalse(RawCell.number(null).isBlank());
        assertFalse(RawCell.number("0").isBlank());
        assertFalse(RawCell.text("商品").isBlank());
        assertTrue(RawCell.blank().isBlank());
    }

    @Test
    @DisplayName("类型不能为空，BLANK不能带有效内容")
    void invalidCellShape() {
        assertThrows(IllegalArgumentException.class, () -> new RawCell(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new RawCell(RawCellType.BLANK, "x"));
    }

    @Test
    @DisplayName("原始行复制Map，缺失列返回空单元格，行号不改变")
    void rawRowCopiesCells() {
        var cells = new HashMap<Integer, RawCell>();
        cells.put(0, RawCell.text("a"));
        var row = new RawImportRow(8, cells);
        cells.clear();
        assertEquals(8, row.rowNum());
        assertEquals("a", row.cell(0).rawValue());
        assertTrue(row.cell(1).isBlank());
        assertThrows(UnsupportedOperationException.class, () -> row.cells().clear());
    }

    @Test
    @DisplayName("原始行全空判断包含公式和错误类型，负列索引拒绝")
    void rawRowBlankSemantics() {
        assertTrue(new RawImportRow(2, null).allBlank());
        assertTrue(new RawImportRow(2, Map.of(0, RawCell.text("\u3000"), 1, RawCell.blank())).allBlank());
        assertFalse(new RawImportRow(2, Map.of(7, RawCell.formula())).allBlank());
        assertFalse(new RawImportRow(2, Map.of(7, RawCell.error())).allBlank());
        assertThrows(IllegalArgumentException.class, () -> new RawImportRow(2, Map.of(-1, RawCell.text("a"))));
    }
}
