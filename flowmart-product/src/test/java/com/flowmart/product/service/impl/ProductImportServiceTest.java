package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.ImportConverter;
import com.flowmart.product.dto.imports.*;
import com.flowmart.product.entity.ImportBatchEntity;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ImportBatchMapper;
import com.flowmart.product.service.importing.*;
import com.flowmart.product.support.ImportExcelReader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImportServiceTest {
    @Mock ImportBatchMapper batches;
    @Mock ImportBatchTransactions transactions;
    @Mock ImportGroupWriter writer;
    @Mock ImportFailureWriter failures;
    @Mock ImportPrechecker prechecker;
    @Mock ImportFileStorage storage;
    @Mock ImportExcelReader reader;
    @Mock ImportConverter converter;
    @InjectMocks ProductImportService service;
    private ImportRow row(int number, String key) {
        return new ImportRow(number, key, "商品", 1L, null, "main.png", List.of(), List.of(), new BigDecimal("1.00"), "sku.png");
    }
    private void ready() throws Exception { ready(true); }
    private void ready(boolean finalRead) throws Exception {
        var batch = new ImportBatchEntity(); batch.setId(10L); batch.setStatus("RUNNING");
        when(transactions.create(eq(1L), eq(2L), eq("key"), anyString(), eq("file.xlsx"), anyString())).thenReturn(batch);
        when(storage.save(eq(10L), any())).thenReturn("private-file");
        when(reader.read(any(), anyString(), anyString())).thenReturn(new ImportParseResult(List.of(row(2, "a"), row(3, "b")), List.of(), null));
        when(prechecker.check(eq(1L), anyList())).thenReturn(Map.of());
        if (finalRead) when(batches.selectById(10L)).thenReturn(batch);
    }
    private void run() { service.submit(1L, 2L, "key", new byte[]{1}, "file.xlsx", ImportExcelReader.MIME); }

    @Test @DisplayName("已知业务拒绝保存失败后继续其他组")
    void businessFailureContinues() throws Exception {
        ready();
        doThrow(new ImportGroupRejectedException(ProductErrorCode.BRAND_DISABLED, "brandId")).doNothing()
                .when(writer).write(eq(10L), eq(1L), eq(2L), any());
        run();
        verify(writer, times(2)).write(eq(10L), eq(1L), eq(2L), any());
        verify(failures).rejected(eq(10L), eq(1L), eq(2L), any(), any());
        verify(transactions).finish(10L, 1L, false, null);
    }
    @Test @DisplayName("数据库系统异常立即中止，不继续后续组、不伪造业务失败")
    void systemFailureStops() throws Exception {
        ready(); doThrow(new IllegalStateException("commit uncertain")).when(writer).write(anyLong(), anyLong(), anyLong(), any());
        run();
        verify(writer).write(anyLong(), anyLong(), anyLong(), any()); verifyNoInteractions(failures);
        verify(transactions).finish(eq(10L), eq(1L), eq(true), anyString());
        verify(transactions, never()).finish(10L, 1L, false, null);
    }
    @Test @DisplayName("记录业务失败自身报错也必须中止，不计成功失败内存数")
    void failureReportStops() throws Exception {
        ready(); doThrow(new ImportGroupRejectedException(ProductErrorCode.IMPORT_ITEM_DUPLICATED, "importItemKey"))
                .when(writer).write(anyLong(), anyLong(), anyLong(), any());
        doThrow(new IllegalStateException("report failed")).when(failures).rejected(anyLong(), anyLong(), anyLong(), any(), any());
        run(); verify(writer).write(anyLong(), anyLong(), anyLong(), any());
        verify(transactions).finish(eq(10L), eq(1L), eq(true), anyString());
    }
    @Test @DisplayName("收尾失败即使所有组处理完也标记中断，而不是宣称全部成功")
    void finalizationFailure() throws Exception {
        ready(); doThrow(new IllegalStateException("finalization failed")).when(transactions).finish(10L, 1L, false, null);
        run(); verify(transactions).finish(eq(10L), eq(1L), eq(true), anyString());
    }
    @Test @DisplayName("连中断汇总也失败时返回带批次ID的明确错误")
    void bothFinalizationsFail() throws Exception {
        ready(false); doThrow(new IllegalStateException("failed")).when(transactions).finish(anyLong(), anyLong(), anyBoolean(), nullable(String.class));
        var error = assertThrows(BizException.class, this::run);
        assertEquals(ProductErrorCode.IMPORT_ABORTED.getCode(), error.getCode()); assertTrue(error.getMessage().contains("10"));
    }
    @Test @DisplayName("批次主键冲突不能冒充幂等重放")
    void unrelatedUniqueConflict() {
        when(transactions.create(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("conflict", new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)));
        assertThrows(DuplicateKeyException.class, this::run); verifyNoInteractions(reader, writer, failures, storage);
    }
    @Test @DisplayName("超大文件、非法归属或空请求键在创建批次前拒绝")
    void requestValidation() {
        assertThrows(BizException.class, () -> service.submit(-1L, 2L, "key", new byte[0], "file.xlsx", ImportExcelReader.MIME));
        assertThrows(BizException.class, () -> service.submit(1L, 2L, " ", new byte[0], "file.xlsx", ImportExcelReader.MIME));
        assertThrows(BizException.class, () -> service.submit(1L, 2L, "key", new byte[ImportExcelReader.MAX_BYTES + 1], "file.xlsx", ImportExcelReader.MIME));
        verifyNoInteractions(transactions, storage, writer);
    }
}
