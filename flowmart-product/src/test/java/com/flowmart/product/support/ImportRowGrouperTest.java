package com.flowmart.product.support;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.*;
import com.flowmart.product.dto.imports.ImportGroup;
import com.flowmart.product.dto.imports.ImportGroupingResult;
import com.flowmart.product.dto.imports.ImportRow;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpecCombinationGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ImportRowGrouperTest {
    private final ImportRowGrouper grouper = new ImportRowGrouper();

    @Test
    @DisplayName("空输入返回空分组结果，文件层负责拒绝空文件")
    void emptyInput() {
        assertEquals(new ImportGroupingResult(List.of(), List.of(), List.of()), grouper.group(null));
        assertEquals(grouper.group(null), grouper.group(List.of()));
    }

    @Test
    @DisplayName("大小写和全角空白归一，非连续行同组，组及原始行顺序不变")
    void groupsNonContiguousRows() {
        var a1 = colorRow(2, " \u3000item-a ", "红");
        var b = row(3, "ITEM-B", List.of(), List.of());
        var a2 = colorRow(8, "ITEM-A", "蓝");
        var result = grouper.group(List.of(a1, b, a2));
        assertEquals(List.of("ITEM-A", "ITEM-B"),
                result.groups().stream().map(ImportGroup::importItemKeyNormalized).toList());
        assertEquals(List.of(2, 8), result.groups().getFirst().rows().stream().map(ImportRow::rowNum).toList());
        assertEquals(a1.importItemKey(), result.groups().getFirst().importItemKey());
        assertEquals(2, result.groups().getFirst().rowCount());
        assertTrue(result.invalidGroups().isEmpty());
        assertTrue(result.ungroupedRows().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "商品1", "a b", "a/b"})
    @DisplayName("非法标识单独记录，合法组继续处理")
    void invalidKeyDoesNotAbort(String key) {
        var result = grouper.group(List.of(row(2, key, List.of(), List.of()),
                row(9, "valid", List.of(), List.of())));
        assertEquals(1, result.ungroupedRows().size());
        assertEquals(2, result.ungroupedRows().getFirst().rowNum());
        assertEquals(key, result.ungroupedRows().getFirst().importItemKey());
        assertEquals(ProductErrorCode.IMPORT_ITEM_KEY_INVALID.getMessage(),
                result.ungroupedRows().getFirst().errorMessage());
        assertEquals("VALID", result.groups().getFirst().importItemKeyNormalized());
        assertTrue(result.invalidGroups().isEmpty());
    }

    @Test
    @DisplayName("超长标识保留原文用于错误展示，不能截断后当成合法键")
    void tooLongKey() {
        invalidKeyDoesNotAbort("a".repeat(65));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "category", "brand", "image", "definition"})
    @DisplayName("五个公共字段不一致时整组失败，保存非连续行而非伪造连续范围")
    void inconsistentFields(String field) {
        var first = colorRow(2, "a", "红");
        var second = new ImportRow(8, "A",
                field.equals("name") ? "不同名称" : first.spuName(),
                field.equals("category") ? 2L : first.categoryId(),
                field.equals("brand") ? Long.valueOf(3L) : first.brandId(),
                field.equals("image") ? "different.png" : first.mainImage(),
                field.equals("definition") ? List.of(spec("颜色", "红", "绿")) : first.specDefinition(),
                List.of(value("颜色", "蓝")), first.price(), first.skuImage());
        var result = grouper.group(List.of(first, row(3, "b", List.of(), List.of()), second));
        var bad = result.invalidGroups().getFirst();
        assertEquals("GROUP_FIELD_INCONSISTENT", bad.errorCode());
        assertEquals(ProductErrorCode.GROUP_FIELD_INCONSISTENT.getMessage(), bad.errorMessage());
        assertEquals(List.of(2, 8), bad.rows().stream().map(ImportRow::rowNum).toList());
        assertEquals(2, bad.rowFrom());
        assertEquals(8, bad.rowTo());
        assertEquals(2, bad.rowCount());
        assertEquals("B", result.groups().getFirst().importItemKeyNormalized());
    }

    @Test
    @DisplayName("无规格单行成功且空品牌合法，无规格多行失败")
    void defaultSku() {
        var first = row(2, "a", null, null);
        var result = grouper.group(List.of(first));
        assertEquals(1, result.groups().size());
        assertNull(result.groups().getFirst().brandId());
        assertTrue(result.groups().getFirst().specDefinition().isEmpty());
        assertFailure(List.of(first, row(5, "a", List.of(), List.of())), "GROUP_NO_SPEC_MULTIPLE_ROWS");
    }

    @Test
    @DisplayName("首尾空白和定义排序不改变语义，成功结果清洗但不修改输入")
    void normalizedDefinitionsAndIndependentSkuFields() {
        var definition = List.of(spec(" 尺寸 ", " M ", " S "), spec("颜色", "红", "蓝"));
        var first = new ImportRow(2, "a", " T恤 ", 1L, null, " main.png ", definition,
                List.of(value("尺寸", "M"), value("颜色", "红")), BigDecimal.ONE, "red.png");
        var second = new ImportRow(7, "A", "T恤", 1L, null, "main.png",
                List.of(spec("颜色", "蓝", "红"), spec("尺寸", "S", "M")),
                List.of(value("颜色", "蓝"), value("尺寸", "S")), BigDecimal.TEN, "blue.png");
        var result = grouper.group(List.of(first, second));
        var group = result.groups().getFirst();
        assertEquals("T恤", group.spuName());
        assertEquals("main.png", group.mainImage());
        assertEquals(List.of("尺寸", "颜色"), group.specDefinition().stream().map(SpecDTO::getName).toList());
        assertEquals(List.of("M", "S"), group.specDefinition().getFirst().getValues());
        assertEquals(" 尺寸 ", definition.getFirst().getName());
        assertEquals(List.of(" M ", " S "), definition.getFirst().getValues());
        assertNotSame(definition.getFirst(), group.specDefinition().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> group.rows().clear());
    }

    @Test
    @DisplayName("规格组合排序和首尾空白不同仍属于重复组合")
    void rejectsDuplicateCombinations() {
        var specs = List.of(spec("颜色", "红"), spec("尺寸", "M"));
        assertFailure(List.of(
                row(2, "a", specs, List.of(value("颜色", "红"), value("尺寸", "M"))),
                row(3, "a", specs, List.of(value(" 尺寸 ", " M "), value("颜色", "红")))),
                "GROUP_SPEC_COMBINATION_DUPLICATE");
    }

    static Stream<Arguments> badDefinitions() {
        return Stream.of(
                Arguments.of(Arrays.asList((SpecDTO) null), "GROUP_SPEC_DEFINITION_INVALID"),
                Arguments.of(List.of(spec(" ", "红")), "GROUP_SPEC_DEFINITION_INVALID"),
                Arguments.of(List.of(spec("颜色", " ")), "GROUP_SPEC_DEFINITION_INVALID"),
                Arguments.of(List.of(spec("颜色", (String) null)), "GROUP_SPEC_DEFINITION_INVALID"),
                Arguments.of(List.of(spec("颜色", (String[]) null)), "SPEC_VALUES_EMPTY"),
                Arguments.of(List.of(spec("颜色")), "SPEC_VALUES_EMPTY"),
                Arguments.of(List.of(spec("颜色", "红"), spec(" 颜色 ", "蓝")), "SPEC_NAME_DUPLICATE"),
                Arguments.of(List.of(spec("颜色", "红", " 红 ")), "SPEC_VALUE_DUPLICATE")
        );
    }

    @ParameterizedTest
    @MethodSource("badDefinitions")
    @DisplayName("首行和后续行的非法规格均报告整组失败，不静默丢弃或去重")
    void rejectsMalformedDefinition(List<SpecDTO> definition, String code) {
        var malformed = row(7, "a", definition, List.of());
        assertFailure(List.of(malformed), code);
        assertFailure(List.of(row(2, "a", List.of(), List.of()), malformed), code);
    }

    static Stream<Arguments> invalidCombinations() {
        return Stream.of(
                Arguments.of(List.of()),
                Arguments.of(List.of(value("颜色", "绿"))),
                Arguments.of(List.of(value("不存在", "红"))),
                Arguments.of(List.of(value("颜色", "红"), value("尺寸", "M"))),
                Arguments.of(List.of(value("颜色", "红"), value(" 颜色 ", "蓝"))),
                Arguments.of(List.of(value(" ", "红"))),
                Arguments.of(List.of(value("颜色", " "))),
                Arguments.of(Arrays.asList((SkuSpecValueDTO) null))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    @DisplayName("组合缺失、越界、重复维度和非法项不能通过")
    void rejectsInvalidCombination(List<SkuSpecValueDTO> combination) {
        assertFailure(List.of(row(2, "a", List.of(spec("颜色", "红", "蓝")), combination)), "SKU_SPEC_MISMATCH");
    }

    @Test
    @DisplayName("无规格商品不能携带规格组合")
    void defaultSkuCannotHaveCombination() {
        assertFailure(List.of(row(2, "a", List.of(), List.of(value("颜色", "红")))), "SKU_SPEC_MISMATCH");
    }

    @Test
    @DisplayName("缺少一个必需规格维度时拒绝")
    void rejectsMissingDimension() {
        assertFailure(List.of(row(2, "a", List.of(spec("颜色", "红"), spec("尺寸", "M")),
                List.of(value("颜色", "红")))), "SKU_SPEC_MISMATCH");
    }

    @Test
    @DisplayName("定义长度、维度和值数量限制复用既有生成器")
    void definitionLimits() {
        for (var definition : List.of(
                List.of(spec("a".repeat(33), "x")),
                List.of(spec("a", "x".repeat(65))),
                List.of(spec("a", "x"), spec("b", "x"), spec("c", "x"), spec("d", "x")),
                List.of(spec("a", IntStream.range(0, 21).mapToObj(String::valueOf).toArray(String[]::new))))) {
            assertFailure(List.of(row(2, "a", definition, List.of())), "GROUP_SPEC_DEFINITION_INVALID");
        }
    }

    @Test
    @DisplayName("1000个组合合法，超出组合或行数上限失败")
    void thousandSkuBoundary() {
        String[] values = IntStream.range(0, 10).mapToObj(String::valueOf).toArray(String[]::new);
        var definitions = List.of(spec("a", values), spec("b", values), spec("c", values));
        var combinations = SpecCombinationGenerator.generate(definitions);
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 0; i < combinations.size(); i++) rows.add(row(i + 2, "a", definitions, combinations.get(i)));
        assertEquals(1000, grouper.group(rows).groups().getFirst().rowCount());
        rows.add(row(1002, "a", definitions, combinations.getFirst()));
        assertFailure(rows, "TOO_MANY_COMBINATIONS");
        String[] eleven = IntStream.range(0, 11).mapToObj(String::valueOf).toArray(String[]::new);
        assertFailure(List.of(row(2, "a", List.of(spec("a", values), spec("b", values), spec("c", eleven)),
                List.of())), "TOO_MANY_COMBINATIONS");
    }

    @Test
    @DisplayName("不同大小写规格值不合并")
    void caseSensitiveValues() {
        var specs = List.of(spec("颜色", "Red", "red"));
        assertEquals(1, grouper.group(List.of(row(2, "a", specs, List.of(value("颜色", "Red"))),
                row(3, "a", specs, List.of(value("颜色", "red"))))).groups().size());
    }

    @Test
    @DisplayName("解析器传入空行对象、表头行号或重复真实行号时明确拒绝")
    void rejectsBrokenParserContract() {
        for (List<ImportRow> input : List.of(
                Arrays.asList((ImportRow) null),
                List.of(row(1, "a", List.of(), List.of())),
                List.of(row(2, "a", List.of(), List.of()), row(2, "b", List.of(), List.of())))) {
            assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                    assertThrows(BizException.class, () -> grouper.group(input)).getCode());
        }
    }

    private void assertFailure(List<ImportRow> rows, String code) {
        var result = grouper.group(rows);
        assertTrue(result.groups().isEmpty());
        assertTrue(result.ungroupedRows().isEmpty());
        assertEquals(1, result.invalidGroups().size());
        assertEquals(code, result.invalidGroups().getFirst().errorCode());
        assertFalse(result.invalidGroups().getFirst().errorMessage().isBlank());
    }

    private static ImportRow colorRow(int number, String key, String color) {
        return row(number, key, List.of(spec("颜色", "红", "蓝")), List.of(value("颜色", color)));
    }

    private static ImportRow row(int number, String key, List<SpecDTO> definition, List<SkuSpecValueDTO> combination) {
        return new ImportRow(number, key, "T恤", 1L, null, "main.png", definition, combination, BigDecimal.ONE, "sku.png");
    }

    private static SpecDTO spec(String name, String... values) {
        var spec = new SpecDTO();
        spec.setName(name);
        spec.setValues(values == null ? null : Arrays.asList(values));
        return spec;
    }

    private static SkuSpecValueDTO value(String name, String selected) {
        var value = new SkuSpecValueDTO();
        value.setName(name);
        value.setValue(selected);
        return value;
    }
}
