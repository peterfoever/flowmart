package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.flowmart.product.dto.imports.*;
import com.flowmart.product.entity.ImportRowErrorEntity;
import com.flowmart.product.mapper.*;
import com.flowmart.product.support.ImportTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import static com.flowmart.product.service.importing.ImportGroupWriter.requireOne;

@Service
@RequiredArgsConstructor
public class ImportFailureWriter {
    private final ImportBatchMapper batches;
    private final ImportGroupResultMapper groups;
    private final ImportRowErrorMapper errors;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void invalid(Long batch, Long owner, Long actor, InvalidGroup group) {
        lock(batch, owner);
        String name = group.rows().isEmpty() ? null : group.rows().getFirst().spuName();
        var result = ImportTexts.result(batch, actor, group.importItemKey(), group.importItemKeyNormalized(), name, group.rowCount());
        result.setStatus("FAILED"); result.setErrorCode(group.errorCode()); result.setErrorMessage(ImportTexts.clip(group.errorMessage(), 512));
        requireOne(groups.insert(result));
        for (var error : group.parseErrors()) row(batch, result.getId(), error.rowNum(), error.importItemKey(), error.field(), error.errorCode(), error.errorMessage());
        for (var parsed : group.rows()) row(batch, result.getId(), parsed.rowNum(), parsed.importItemKey(),
                "GROUP", "GROUP_FAILED", group.errorMessage());
        requireOne(batches.heartbeat(batch, LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void rejected(Long batch, Long owner, Long actor, ImportGroup group, ImportGroupRejectedException failure) {
        lock(batch, owner);
        var result = ImportTexts.result(batch, actor, group.importItemKey(), group.importItemKeyNormalized(), group.spuName(), group.rowCount());
        result.setStatus("FAILED"); result.setErrorCode(failure.code()); result.setErrorMessage(ImportTexts.clip(failure.getMessage(), 512));
        requireOne(groups.insert(result));
        // 引用/来源错误作用于全组，每行都保留相同具体原因。
        for (var parsed : group.rows()) row(batch, result.getId(), parsed.rowNum(), parsed.importItemKey(), failure.field(), failure.code(), failure.getMessage());
        requireOne(batches.heartbeat(batch, LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void ungrouped(Long batch, Long owner, List<UngroupedRow> rows) {
        lock(batch, owner);
        for (var row : rows) row(batch, null, row.rowNum(), row.importItemKey(), row.field(), row.errorCode(), row.errorMessage());
        requireOne(batches.heartbeat(batch, LocalDateTime.now()));
    }

    private void lock(Long batch, Long owner) {
        if (!"RUNNING".equals(batches.lockStatus(batch, owner))) throw new IllegalStateException("批次不在执行中");
    }
    private void row(Long batch, Long group, int number, String rawKey, String field, String code, String message) {
        var entity = new ImportRowErrorEntity();
        entity.setId(IdWorker.getId()); entity.setBatchId(batch); entity.setGroupResultId(group); entity.setRowNum(number);
        entity.setImportItemKey(ImportTexts.clip(rawKey, 64)); entity.setField(field); entity.setErrorCode(code);
        entity.setErrorMessage(ImportTexts.clip(message, 512)); entity.setCreatedAt(LocalDateTime.now());
        requireOne(errors.insert(entity));
    }
}
