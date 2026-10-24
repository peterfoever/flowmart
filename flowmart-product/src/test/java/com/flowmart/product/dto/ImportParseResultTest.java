package com.flowmart.product.dto;

import com.flowmart.product.dto.imports.FileLevelError;
import com.flowmart.product.dto.imports.ImportParseResult;
import com.flowmart.product.dto.imports.ImportRow;
import com.flowmart.product.dto.imports.RowError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportParseResultTest {
    private final ImportRow row = new ImportRow(2, "a", "商品", 1L, null,
            "main.png", List.of(), List.of(), BigDecimal.ONE, "sku.png");
    private final RowError rowError = new RowError(3, "b", "B", "price", "SKU_PRICE_INVALID", "价格非法");
    private final FileLevelError fileError = new FileLevelError("HEADER_MISMATCH", "表头不匹配");

    @Test
    @DisplayName("成功解析和行级失败可同时保存，不视为文件级失败")
    void retainsBothKindsOfRows() {
        var result = new ImportParseResult(List.of(row), List.of(rowError), null);
        assertFalse(result.hasFileLevelError());
        assertEquals(List.of(row), result.rows());
        assertEquals(List.of(rowError), result.rowErrors());
    }

    @Test
    @DisplayName("文件级失败只保存拒绝原因")
    void fileRejection() {
        var result = new ImportParseResult(null, null, fileError);
        assertTrue(result.hasFileLevelError());
        assertEquals(fileError, result.fileLevelError());
        assertTrue(result.rows().isEmpty());
        assertTrue(result.rowErrors().isEmpty());
    }

    @Test
    @DisplayName("文件级错误不能混入部分解析行，防止文件拒绝后仍继续导入")
    void rejectsPartialResultsOnFileFailure() {
        assertThrows(IllegalArgumentException.class, () -> new ImportParseResult(List.of(row), List.of(), fileError));
        assertThrows(IllegalArgumentException.class, () -> new ImportParseResult(List.of(), List.of(rowError), fileError));
    }

    @Test
    @DisplayName("解析结果复制列表，避免分组时外部删掉失败行")
    void snapshotsLists() {
        var rows = new ArrayList<>(List.of(row));
        var errors = new ArrayList<>(List.of(rowError));
        var result = new ImportParseResult(rows, errors, null);
        rows.clear();
        errors.clear();
        assertEquals(1, result.rows().size());
        assertEquals(1, result.rowErrors().size());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rowErrors().clear());
    }

    @Test
    @DisplayName("空解析结果不自动标记文件失败，空文件判定由Reader负责")
    void emptyResultDoesNotInventError() {
        var result = new ImportParseResult(null, null, null);
        assertFalse(result.hasFileLevelError());
        assertTrue(result.rows().isEmpty());
        assertTrue(result.rowErrors().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("文件错误码和说明不能缺失")
    void invalidFileError(String missing) {
        assertThrows(IllegalArgumentException.class, () -> new FileLevelError(missing, "错误说明"));
        assertThrows(IllegalArgumentException.class, () -> new FileLevelError("HEADER_MISMATCH", missing));
    }
}
