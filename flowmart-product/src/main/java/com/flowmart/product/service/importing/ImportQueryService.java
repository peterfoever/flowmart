package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowmart.common.exception.*;
import com.flowmart.common.result.PageResult;
import com.flowmart.product.convert.ImportConverter;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.*;
import com.flowmart.product.vo.imports.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
@RequiredArgsConstructor
public class ImportQueryService {
    private final ImportBatchMapper batches;
    private final ImportGroupResultMapper groups;
    private final ImportRowErrorMapper errors;
    private final ImportConverter converter;

    public ImportBatchVO detail(Long id, Long owner) { return converter.toBatch(requireOwned(id, owner)); }
    public PageResult<ImportGroupVO> groups(Long id, Long owner, int page, int size) {
        requireOwned(id, owner); checkPage(page, size);
        return PageResult.of(groups.selectPage(new Page<>(page, size), Wrappers.<ImportGroupResultEntity>lambdaQuery()
                .eq(ImportGroupResultEntity::getBatchId, id).orderByAsc(ImportGroupResultEntity::getId)), converter::toGroup);
    }
    public PageResult<ImportRowErrorVO> errors(Long id, Long owner, int page, int size) {
        requireOwned(id, owner); checkPage(page, size);
        return PageResult.of(errors.selectPage(new Page<>(page, size), Wrappers.<ImportRowErrorEntity>lambdaQuery()
                .eq(ImportRowErrorEntity::getBatchId, id).orderByAsc(ImportRowErrorEntity::getRowNum)), converter::toError);
    }
    public byte[] report(Long id, Long owner) throws IOException {
        requireOwned(id, owner);
        var rows = errors.selectList(Wrappers.<ImportRowErrorEntity>lambdaQuery().eq(ImportRowErrorEntity::getBatchId, id)
                .orderByAsc(ImportRowErrorEntity::getRowNum).last("LIMIT 1000"));
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("导入错误");
            String[] headers = {"原始行号", "importItemKey", "字段", "错误码", "错误说明"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { header.createCell(i).setCellValue(headers[i]); sheet.setColumnWidth(i, 25 * 256); }
            int index = 1;
            for (var error : rows) {
                var row = sheet.createRow(index++);
                // 显式字符串单元格，不把用户可控的=、+、@前缀写成公式。
                String[] values = {String.valueOf(error.getRowNum()), error.getImportItemKey(), error.getField(), error.getErrorCode(), error.getErrorMessage()};
                for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
            }
            book.write(out); return out.toByteArray();
        }
    }
    private ImportBatchEntity requireOwned(Long id, Long owner) {
        if (id == null || id <= 0 || owner == null || owner < 0) throw new BizException(CommonErrorCode.PARAM_INVALID);
        var batch = batches.selectOne(Wrappers.<ImportBatchEntity>lambdaQuery().eq(ImportBatchEntity::getId, id).eq(ImportBatchEntity::getOwnerId, owner));
        if (batch == null) throw new BizException(ProductErrorCode.IMPORT_BATCH_NOT_FOUND);
        return batch;
    }
    private void checkPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BizException(CommonErrorCode.PARAM_INVALID);
    }
}
