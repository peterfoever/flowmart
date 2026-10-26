package com.flowmart.product.support;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.imports.*;
import com.flowmart.product.enums.ProductErrorCode;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ImportRowGrouperParseErrorTest {
    private final ImportRowGrouper grouper = new ImportRowGrouper();

    @Test
    @DisplayName("同组两行成功一行失败时整组隔离，其他商品组仍然合法")
    void mixedGroupCannotPartiallyImport() {
        var error = error(3, "ITEM-001", "ITEM-001");
        var result = grouper.group(List.of(row(4, "ITEM-001"), row(2, "item-001"), row(5, "ITEM-002")),
                List.of(error));
        assertEquals(List.of("ITEM-002"), result.groups().stream().map(ImportGroup::importItemKeyNormalized).toList());
        assertTrue(result.ungroupedRows().isEmpty());
        var failed = result.invalidGroups().getFirst();
        assertEquals(1, result.invalidGroups().size());
        assertEquals("ITEM-001", failed.importItemKeyNormalized());
        assertEquals(ProductErrorCode.PARSE_ROW_FAILED.name(), failed.errorCode());
        assertEquals(ProductErrorCode.PARSE_ROW_FAILED.getMessage(), failed.errorMessage());
        assertEquals(3, failed.rowCount());
        assertEquals(2, failed.rowFrom());
        assertEquals(4, failed.rowTo());
        assertEquals(List.of(2, 4), failed.rows().stream().map(ImportRow::rowNum).toList());
        assertEquals(List.of(error), failed.parseErrors());
        assertEquals("price", failed.parseErrors().getFirst().field());
        assertEquals("SKU_PRICE_INVALID", failed.parseErrors().getFirst().errorCode());
    }

    @Test
    @DisplayName("只有错误行也能产生完整失败组，不能因成功行列表为空而提前返回")
    void onlyErrorsStillCreateGroups() {
        var result = grouper.group(null, List.of(error(9, "B", "B"),
                error(7, "A", "A"), error(2, " a ", "A")));
        assertTrue(result.groups().isEmpty());
        assertEquals(List.of("A", "B"),
                result.invalidGroups().stream().map(InvalidGroup::importItemKeyNormalized).toList());
        var failed = result.invalidGroups().getFirst();
        assertTrue(failed.rows().isEmpty());
        assertEquals(List.of(2, 7), failed.parseErrors().stream().map(RowError::rowNum).toList());
        assertEquals(2, failed.rowCount());
        assertEquals(" a ", failed.importItemKey());
        assertEquals(2, failed.rowFrom());
        assertEquals(7, failed.rowTo());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "非法标识", "a b"})
    @DisplayName("非法原始标识只产生无法归组的错误，保留字段和具体原因")
    void invalidKeyIsUngrouped(String rawKey) {
        var error = new RowError(3, rawKey, null, "importItemKey", "IMPORT_ITEM_KEY_INVALID", "标识非法");
        var result = grouper.group(List.of(row(2, "A")), List.of(error));
        assertEquals(1, result.groups().size());
        assertTrue(result.invalidGroups().isEmpty());
        assertEquals(new UngroupedRow(3, rawKey, "importItemKey", "IMPORT_ITEM_KEY_INVALID", "标识非法"),
                result.ungroupedRows().getFirst());
    }

    @Test
    @DisplayName("大小写和全角空白规范化一致，首行错误也能成为组原始标识来源")
    void normalizedKeyAndEarliestRawKey() {
        var result = grouper.group(List.of(row(8, "ITEM-A")), List.of(error(2, " \u3000item-a ", "ITEM-A")));
        assertTrue(result.groups().isEmpty());
        assertEquals(" \u3000item-a ", result.invalidGroups().getFirst().importItemKey());
        assertEquals(2, result.invalidGroups().getFirst().rowCount());
    }

    @Test
    @DisplayName("所有结果按真实行号稳定排序，原始输入列表不被排序或修改")
    void stableOrderingAcrossBothLists() {
        var rows = new ArrayList<>(List.of(row(20, "OK-LATE"), row(9, "B"), row(7, "A"),
                row(4, "OK-EARLY"), row(18, " ")));
        var errors = new ArrayList<>(List.of(error(12, "A", "A"), error(2, "A", "A"),
                error(8, "B", "B"), error(3, "bad key", null)));
        var originalRows = List.copyOf(rows);
        var originalErrors = List.copyOf(errors);
        var result = grouper.group(rows, errors);
        assertEquals(List.of("OK-EARLY", "OK-LATE"),
                result.groups().stream().map(ImportGroup::importItemKeyNormalized).toList());
        assertEquals(List.of("A", "B"),
                result.invalidGroups().stream().map(InvalidGroup::importItemKeyNormalized).toList());
        assertEquals(List.of(3, 18), result.ungroupedRows().stream().map(UngroupedRow::rowNum).toList());
        assertEquals(List.of(2, 12), result.invalidGroups().getFirst().parseErrors().stream().map(RowError::rowNum).toList());
        assertEquals(originalRows, rows);
        assertEquals(originalErrors, errors);
    }

    @Test
    @DisplayName("普通规格校验失败继续走旧路径，解析错误列表为空")
    void existingGroupValidationStillRuns() {
        var result = grouper.group(List.of(row(2, "A"), row(3, "A")), List.of());
        assertEquals("GROUP_NO_SPEC_MULTIPLE_ROWS", result.invalidGroups().getFirst().errorCode());
        assertTrue(result.invalidGroups().getFirst().parseErrors().isEmpty());
        assertEquals(result, grouper.group(List.of(row(2, "A"), row(3, "A"))));
    }

    @Test
    @DisplayName("旧入口非法标识仍能分流，并补齐错误字段和错误码")
    void legacyUngroupedErrorHasMetadata() {
        var ungrouped = grouper.group(List.of(row(2, " "))).ungroupedRows().getFirst();
        assertEquals("importItemKey", ungrouped.field());
        assertEquals("IMPORT_ITEM_KEY_INVALID", ungrouped.errorCode());
    }

    @Test
    @DisplayName("所有行恰好落入一种结果，失败组计数包含两种行")
    void accountsForEveryInputRowOnce() {
        var rows = List.of(row(2, "A"), row(4, "A"), row(5, "B"), row(10, " "));
        var errors = List.of(error(3, "A", "A"), error(6, "C", "C"), error(9, "", null));
        var result = grouper.group(rows, errors);
        var numbers = new ArrayList<Integer>();
        result.groups().forEach(g -> g.rows().forEach(r -> numbers.add(r.rowNum())));
        result.invalidGroups().forEach(g -> {
            assertEquals(g.rows().size() + g.parseErrors().size(), g.rowCount());
            g.rows().forEach(r -> numbers.add(r.rowNum()));
            g.parseErrors().forEach(r -> numbers.add(r.rowNum()));
        });
        result.ungroupedRows().forEach(r -> numbers.add(r.rowNum()));
        assertEquals(List.of(2, 3, 4, 5, 6, 9, 10), numbers.stream().sorted().toList());
    }

    @Test
    @DisplayName("输入与输出列表隔离，不能从结果中删除错误行")
    void failureListsAreSnapshots() {
        var rows = new ArrayList<>(List.of(row(2, "A")));
        var errors = new ArrayList<>(List.of(error(3, "A", "A")));
        var failed = grouper.group(rows, errors).invalidGroups().getFirst();
        rows.clear();
        errors.clear();
        assertEquals(1, failed.rows().size());
        assertEquals(1, failed.parseErrors().size());
        assertThrows(UnsupportedOperationException.class, () -> failed.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> failed.parseErrors().clear());
    }

    @Test
    @DisplayName("空的双列表兼容旧入口")
    void emptyInputs() {
        assertEquals(grouper.group(List.of()), grouper.group(null, null));
        assertEquals(grouper.group(List.of(row(2, "A"))), grouper.group(List.of(row(2, "A")), null));
    }

    @Test
    @DisplayName("成功行与错误行重叠或各自重复都明确拒绝，不能按行号静默去重")
    void duplicateRowsAreRejected() {
        assertInvalid(() -> grouper.group(List.of(row(2, "A")), List.of(error(2, "A", "A"))));
        assertInvalid(() -> grouper.group(List.of(row(2, "A"), row(2, "B")), List.of()));
        assertInvalid(() -> grouper.group(List.of(), List.of(error(2, "A", "A"), error(2, "B", "B"))));
        assertInvalid(() -> grouper.group(List.of(row(2, " ")), List.of(error(2, "", null))));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("错误行的行号也必须从2开始")
    void invalidErrorRowNumber(int rowNum) {
        assertInvalid(() -> grouper.group(List.of(), List.of(error(rowNum, "A", "A"))));
    }

    @Test
    @DisplayName("空行对象和空错误对象不能被忽略")
    void nullElements() {
        assertInvalid(() -> grouper.group(Arrays.asList((ImportRow) null), List.of()));
        assertInvalid(() -> grouper.group(List.of(), Arrays.asList((RowError) null)));
    }

    static Stream<Arguments> inconsistentKeys() {
        return Stream.of(
                Arguments.of("a", null),
                Arguments.of("a", "B"),
                Arguments.of("a", "a"),
                Arguments.of("a", ""),
                Arguments.of("a", " A "),
                Arguments.of("bad key", "BAD"),
                Arguments.of(null, "A")
        );
    }

    @ParameterizedTest
    @MethodSource("inconsistentKeys")
    @DisplayName("错误行标识漏填、伪造或未规范化时拒绝，不能让其脱离原组")
    void rejectInconsistentNormalizedKeys(String raw, String normalized) {
        assertInvalid(() -> grouper.group(List.of(), List.of(error(2, raw, normalized))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u3000"})
    @DisplayName("行错误的错误码和说明必须存在")
    void missingErrorDetails(String missing) {
        assertInvalid(() -> grouper.group(List.of(), List.of(new RowError(2, "A", "A", "price", missing, "失败"))));
        assertInvalid(() -> grouper.group(List.of(), List.of(new RowError(2, "A", "A", "price", "PRICE_INVALID", missing))));
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), assertThrows(BizException.class, action).getCode());
    }

    private static ImportRow row(int rowNum, String key) {
        return new ImportRow(rowNum, key, "T恤", 1L, null, "main.png", List.of(), List.of(), BigDecimal.ONE, "sku.png");
    }

    private static RowError error(int rowNum, String rawKey, String normalized) {
        return new RowError(rowNum, rawKey, normalized, "price", "SKU_PRICE_INVALID", "销售价格不合法");
    }
}
