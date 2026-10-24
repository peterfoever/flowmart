package com.flowmart.product.support;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.imports.*;
import com.flowmart.product.enums.RawCellType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ImportRowValidatorTest {
    private final ImportRowValidator validator = new ImportRowValidator();
    private final ImportRowGrouper grouper = new ImportRowGrouper();

    @Test
    @DisplayName("正常无品牌无规格行保留长ID、真实行号和原始标识")
    void validDefaultRow() {
        var raw = row(8, ImportColumns.IMPORT_ITEM_KEY, RawCell.text(" \u3000item-1 "));
        var result = validator.validate(List.of(raw));
        assertTrue(result.rowErrors().isEmpty());
        assertFalse(result.hasFileLevelError());
        var parsed = result.rows().getFirst();
        assertEquals(8, parsed.rowNum());
        assertEquals(" \u3000item-1 ", parsed.importItemKey());
        assertEquals(1234567890123456789L, parsed.categoryId());
        assertNull(parsed.brandId());
        assertTrue(parsed.specDefinition().isEmpty());
        assertTrue(parsed.specCombination().isEmpty());
        assertEquals(new BigDecimal("12.30"), parsed.price());
        assertEquals("ITEM-1", grouper.group(result.rows(), result.rowErrors()).groups().getFirst().importItemKeyNormalized());
    }

    @Test
    @DisplayName("有规格JSON保留正确泛型且不改变原始Map")
    void typedSpecs() {
        var cells = validCells();
        cells.put(3, RawCell.text("9223372036854775807"));
        cells.put(5, RawCell.text("[{\"name\":\"颜色\",\"values\":[\"红\",\"蓝\"]}]"));
        cells.put(6, RawCell.text("[{\"name\":\"颜色\",\"value\":\"红\"}]"));
        var snapshot = Map.copyOf(cells);
        var result = validator.validate(List.of(new RawImportRow(2, cells)));
        assertTrue(result.rowErrors().isEmpty());
        var parsed = result.rows().getFirst();
        assertEquals(Long.MAX_VALUE, parsed.brandId());
        assertEquals(List.of("红", "蓝"), parsed.specDefinition().getFirst().getValues());
        assertEquals("红", parsed.specCombination().getFirst().getValue());
        assertEquals(snapshot, cells);
        assertEquals(1, grouper.group(result.rows(), result.rowErrors()).groups().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u3000", "[]", " [ ] "})
    @DisplayName("空白或空数组才表示无规格")
    void emptySpecs(String json) {
        var cells = validCells();
        cells.put(5, RawCell.text(json));
        cells.put(6, RawCell.text(json));
        var result = validator.validate(List.of(new RawImportRow(2, cells)));
        assertTrue(result.rowErrors().isEmpty());
        assertTrue(result.rows().getFirst().specDefinition().isEmpty());
        assertTrue(result.rows().getFirst().specCombination().isEmpty());
    }

    static Stream<Arguments> malformedJson() {
        var common = Stream.of("null", "{}", "1", "true", "\"text\"", "[null]", "[1]", "[] []",
                "[", "[{\"name\":\"a\",\"name\":\"b\"}]", "[{\"name\":1}]", "[{}]");
        var shared = common.flatMap(value -> Stream.of(Arguments.of(5, value), Arguments.of(6, value)));
        return Stream.concat(shared, Stream.of(
                Arguments.of(5, "[{\"name\":\"颜色\",\"values\":[1]}]"),
                Arguments.of(5, "[{\"name\":\"颜色\",\"values\":[true]}]"),
                Arguments.of(5, "[{\"name\":\"颜色\",\"values\":[null]}]"),
                Arguments.of(5, "[{\"name\":\"颜色\",\"values\":\"红\"}]"),
                Arguments.of(5, "[{\"name\":true,\"values\":[\"红\"]}]"),
                Arguments.of(5, "[{\"name\":\"颜色\",\"values\":[],\"extra\":1}]"),
                Arguments.of(6, "[{\"name\":\"颜色\",\"value\":1}]"),
                Arguments.of(6, "[{\"name\":\"颜色\",\"value\":false}]"),
                Arguments.of(6, "[{\"name\":\"颜色\",\"value\":null}]"),
                Arguments.of(6, "[{\"name\":\"颜色\",\"value\":\"红\",\"extra\":1}]")
        ));
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    @DisplayName("拒绝JSON null、非数组、错误字段类型、重复键、尾随内容和未知字段")
    void rejectsMalformedJson(int column, String json) {
        var error = onlyError(row(2, column, RawCell.text(json)));
        assertEquals(column == 5 ? "SPEC_DEFINITION_INVALID" : "SPEC_COMBINATION_INVALID", error.errorCode());
        assertEquals(column == 5 ? "specDefinition" : "specCombination", error.field());
        assertEquals("ITEM-1", error.importItemKeyNormalized());
    }

    @Test
    @DisplayName("JSON深度和长度超限产生行错误，不终止其他合法行")
    void jsonResourceLimits() {
        for (String json : List.of("[".repeat(51) + "]".repeat(51), "[\"" + "a".repeat(4096) + "\"]")) {
            var result = validator.validate(List.of(row(2, 5, RawCell.text(json)), new RawImportRow(4, validCells())));
            assertEquals(1, result.rowErrors().size());
            assertEquals(1, result.rows().size());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a b", "商品1"})
    @DisplayName("非法标识单独分流，不影响后续合法行")
    void invalidKey(String key) {
        var result = validator.validate(List.of(row(2, 0, RawCell.text(key)), new RawImportRow(4, validCells())));
        assertEquals(1, result.rows().size());
        assertEquals(1, result.rowErrors().size());
        assertEquals(key, result.rowErrors().getFirst().importItemKey());
        assertNull(result.rowErrors().getFirst().importItemKeyNormalized());
        var grouped = grouper.group(result.rows(), result.rowErrors());
        assertEquals(1, grouped.groups().size());
        assertEquals(1, grouped.ungroupedRows().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    @DisplayName("类目和品牌ID数字单元格拒绝，不能把可能已丢精度的数字转回文本")
    void numericIdRejected(int column) {
        assertEquals("IMPORT_ID_NOT_TEXT", onlyError(row(2, column,
                RawCell.number("1234567890123456789"))).errorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "1.0", "1e3", "01", "１２", "9223372036854775808", "99999999999999999999"})
    @DisplayName("ID必须为Long范围内的正整数字符串")
    void malformedId(String id) {
        for (int column : new int[]{2, 3}) {
            assertEquals("IMPORT_ID_INVALID", onlyError(row(2, column, RawCell.text(id))).errorCode());
        }
    }

    @Test
    @DisplayName("类目必填而品牌可空，ID首尾全角空白清洗")
    void requiredAndOptionalIds() {
        assertEquals("IMPORT_FIELD_REQUIRED", onlyError(row(2, 2, RawCell.blank())).errorCode());
        var cells = validCells();
        cells.put(2, RawCell.text("\u30001\u3000"));
        cells.put(3, RawCell.text("\u3000"));
        var result = validator.validate(List.of(new RawImportRow(2, cells)));
        assertEquals(1L, result.rows().getFirst().categoryId());
        assertNull(result.rows().getFirst().brandId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "12.30", "99999999.99"})
    @DisplayName("文本和数字价格按BigDecimal原值转换，允许零元与最大值")
    void validPrices(String value) {
        for (var cell : List.of(RawCell.text(value), RawCell.number(value))) {
            var parsed = validator.validate(List.of(row(2, 7, cell))).rows().getFirst();
            assertEquals(new BigDecimal(value), parsed.price());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "-0.01", "12.345", "1.000", "100000000.00", "9999999999999999.99", "十二元", "NaN"})
    @DisplayName("价格格式、符号、小数位和上限非法时保留分组标识")
    void invalidPrices(String value) {
        var error = onlyError(row(2, 7, RawCell.text(value)));
        assertEquals("SKU_PRICE_INVALID", error.errorCode());
        assertEquals("price", error.field());
        assertEquals("ITEM-1", error.importItemKeyNormalized());
    }

    static Stream<Arguments> forbiddenCells() {
        return IntStream.range(0, 9).boxed().flatMap(column -> Stream.of(
                Arguments.of(column, new RawCell(RawCellType.FORMULA, "ITEM-1"), "CELL_FORMULA_NOT_ALLOWED"),
                Arguments.of(column, new RawCell(RawCellType.ERROR, "ITEM-1"), "CELL_ERROR_VALUE")));
    }

    @ParameterizedTest
    @MethodSource("forbiddenCells")
    @DisplayName("所有列拒绝公式和错误类型，标识列不使用缓存值建立商品归属")
    void forbiddenCells(int column, RawCell cell, String code) {
        var error = onlyError(row(2, column, cell));
        assertEquals(code, error.errorCode());
        if (column == 0) {
            assertNull(error.importItemKey());
            assertNull(error.importItemKeyNormalized());
            assertEquals(1, grouper.group(List.of(), List.of(error)).ungroupedRows().size());
        } else {
            assertEquals("ITEM-1", error.importItemKeyNormalized());
            assertEquals(1, grouper.group(List.of(), List.of(error)).invalidGroups().size());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 5, 6, 8})
    @DisplayName("普通文本列不能隐式接受数字单元格")
    void nonTextCells(int column) {
        assertEquals("IMPORT_CELL_TYPE_INVALID", onlyError(row(2, column, RawCell.number("123"))).errorCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 8})
    @DisplayName("名称和图片必填，长度上限包含边界，统一清理全角空白")
    void textBounds(int column) {
        assertEquals(column == 8 ? "SKU_IMAGE_REQUIRED" : "IMPORT_FIELD_REQUIRED",
                onlyError(row(2, column, RawCell.text("\u3000"))).errorCode());
        int max = column == 1 ? 128 : 512;
        assertTrue(validator.validate(List.of(row(2, column, RawCell.text("a".repeat(max))))).rowErrors().isEmpty());
        assertEquals("IMPORT_TEXT_TOO_LONG", onlyError(row(2, column, RawCell.text("a".repeat(max + 1)))).errorCode());
        var parsed = validator.validate(List.of(row(2, column, RawCell.text("\u3000abc\u3000")))).rows().getFirst();
        assertEquals("abc", column == 1 ? parsed.spuName() : column == 4 ? parsed.mainImage() : parsed.skuImage());
    }

    @Test
    @DisplayName("一行多个错误只报告固定顺序的第一个，下一行继续处理")
    void oneErrorPerRow() {
        var cells = validCells();
        cells.put(2, RawCell.number("1"));
        cells.put(7, RawCell.text("十二元"));
        cells.put(8, RawCell.blank());
        var result = validator.validate(List.of(new RawImportRow(2, cells), new RawImportRow(7, validCells())));
        assertEquals(1, result.rowErrors().size());
        assertEquals("categoryId", result.rowErrors().getFirst().field());
        assertEquals(7, result.rows().getFirst().rowNum());
    }

    @Test
    @DisplayName("全空行跳过但不重新编号，公式行不能被空行判断吞掉")
    void blankRowsAndFormulaRows() {
        var result = validator.validate(List.of(new RawImportRow(2, Map.of()),
                new RawImportRow(3, Map.of(0, RawCell.text("\u3000"))),
                new RawImportRow(6, Map.of(0, RawCell.formula())),
                new RawImportRow(9, validCells())));
        assertEquals(List.of(9), result.rows().stream().map(ImportRow::rowNum).toList());
        assertEquals(6, result.rowErrors().getFirst().rowNum());
        assertEquals("CELL_FORMULA_NOT_ALLOWED", result.rowErrors().getFirst().errorCode());
    }

    @Test
    @DisplayName("空列表合法，空对象、非法或重复行号和越界列属于程序契约错误")
    void parserContract() {
        assertTrue(validator.validate(null).rows().isEmpty());
        for (var input : List.of(
                Arrays.asList((RawImportRow) null),
                List.of(new RawImportRow(1, validCells())),
                List.of(new RawImportRow(2, Map.of()), new RawImportRow(2, validCells())),
                List.of(new RawImportRow(2, Map.of(9, RawCell.text("多余列")))))) {
            assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                    assertThrows(BizException.class, () -> validator.validate(input)).getCode());
        }
    }

    @Test
    @DisplayName("Validator串联Grouper：同组一行价格非法时整组失败，其他商品组保留")
    void validatorToGrouper() {
        var b = validCells();
        b.put(0, RawCell.text("item-2"));
        var parsed = validator.validate(List.of(new RawImportRow(2, validCells()),
                row(3, 7, RawCell.text("十二元")), new RawImportRow(4, validCells()), new RawImportRow(5, b)));
        var grouped = grouper.group(parsed.rows(), parsed.rowErrors());
        assertEquals(List.of("ITEM-2"), grouped.groups().stream().map(ImportGroup::importItemKeyNormalized).toList());
        var failed = grouped.invalidGroups().getFirst();
        assertEquals("PARSE_ROW_FAILED", failed.errorCode());
        assertEquals(3, failed.rowCount());
        assertEquals(List.of(2, 4), failed.rows().stream().map(ImportRow::rowNum).toList());
        assertEquals(3, failed.parseErrors().getFirst().rowNum());
    }

    @Test
    @DisplayName("非法JSON不能变成默认SKU，规格业务校验仍由分组器执行")
    void jsonAndBusinessValidationStayDistinct() {
        var parsed = validator.validate(List.of(row(2, 5, RawCell.text("null"))));
        assertEquals("PARSE_ROW_FAILED", grouper.group(parsed.rows(), parsed.rowErrors()).invalidGroups().getFirst().errorCode());
        var cells = validCells();
        cells.put(5, RawCell.text("[{\"name\":\"颜色\",\"values\":[]}]"));
        var validJson = validator.validate(List.of(new RawImportRow(2, cells)));
        assertTrue(validJson.rowErrors().isEmpty());
        assertEquals("SPEC_VALUES_EMPTY", grouper.group(validJson.rows(), validJson.rowErrors()).invalidGroups().getFirst().errorCode());
    }

    private RowError onlyError(RawImportRow raw) {
        var result = validator.validate(List.of(raw));
        assertTrue(result.rows().isEmpty());
        assertEquals(1, result.rowErrors().size());
        return result.rowErrors().getFirst();
    }

    private static RawImportRow row(int rowNum, int column, RawCell cell) {
        var cells = validCells();
        cells.put(column, cell);
        return new RawImportRow(rowNum, cells);
    }

    private static Map<Integer, RawCell> validCells() {
        var cells = new HashMap<Integer, RawCell>();
        cells.put(0, RawCell.text("item-1"));
        cells.put(1, RawCell.text("测试商品"));
        cells.put(2, RawCell.text("1234567890123456789"));
        cells.put(4, RawCell.text("main.png"));
        cells.put(7, RawCell.number("12.30"));
        cells.put(8, RawCell.text("sku.png"));
        return cells;
    }
}
