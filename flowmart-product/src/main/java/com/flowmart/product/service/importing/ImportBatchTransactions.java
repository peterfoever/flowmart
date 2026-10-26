package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.product.entity.*;
import com.flowmart.product.mapper.*;
import com.flowmart.product.support.ImportTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import static com.flowmart.product.service.importing.ImportGroupWriter.requireOne;

/** 每个公开写方法均为短事务。编排服务不持有覆盖整份文件的大事务。 */
@Service
@RequiredArgsConstructor
public class ImportBatchTransactions {
    private final ImportBatchMapper batches;
    private final ImportGroupResultMapper groups;
    private final ImportRowErrorMapper errors;

    /** 独立读事务避免NOT_SUPPORTED编排上下文中MyBatis一级缓存重放先前的“查无批次”。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ImportBatchEntity findByKey(Long owner, String key) {
        return batches.selectOne(Wrappers.<ImportBatchEntity>lambdaQuery()
                .eq(ImportBatchEntity::getOwnerId, owner).eq(ImportBatchEntity::getIdempotencyKey, key));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ImportBatchEntity create(Long owner, Long actor, String key, String fingerprint, String name, String hash) {
        var batch = new ImportBatchEntity();
        batch.setId(IdWorker.getId()); batch.setOwnerId(owner); batch.setOperatorId(actor);
        batch.setIdempotencyKey(key); batch.setRequestFingerprint(fingerprint);
        batch.setFileName(ImportTexts.clip(name, 255)); batch.setFileHash(hash);
        batch.setStatus("PENDING"); batch.setTotalSpuCount(0); batch.setTotalRowCount(0);
        batch.setSuccessSpuCount(0); batch.setFailedSpuCount(0); batch.setUnprocessedSpuCount(0);
        batch.setSuccessRowCount(0); batch.setFailedRowCount(0); batch.setUngroupedFailedRowCount(0);
        long previous = batches.selectCount(Wrappers.<ImportBatchEntity>lambdaQuery()
                .eq(ImportBatchEntity::getOwnerId, owner).eq(ImportBatchEntity::getFileHash, hash));
        batch.setWarnings(previous > 0 ? "[\"FILE_ALREADY_IMPORTED\"]" : "[]");
        batch.setCreatedBy(actor); batch.setUpdatedBy(actor);
        batch.setCreatedAt(LocalDateTime.now()); batch.setUpdatedAt(batch.getCreatedAt());
        requireOne(batches.insert(batch));
        return batch;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void start(Long id, Long owner, int totalGroups, int totalRows, String path) {
        if (!"PENDING".equals(batches.lockStatus(id, owner))) throw new IllegalStateException("批次不能重复启动");
        var batch = batches.selectById(id);
        batch.setStatus("RUNNING"); batch.setFileUrl(path); batch.setTotalSpuCount(totalGroups);
        batch.setUnprocessedSpuCount(totalGroups); batch.setTotalRowCount(totalRows);
        batch.setStartedAt(LocalDateTime.now()); batch.setUpdatedAt(batch.getStartedAt());
        requireOne(batches.updateById(batch));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void rejectFile(Long id, Long owner, String path, String code, String message) {
        if (!"PENDING".equals(batches.lockStatus(id, owner))) throw new IllegalStateException("批次不能拒绝文件");
        var batch = batches.selectById(id);
        batch.setFileUrl(path); batch.setStatus("FAILED"); batch.setErrorSummary(ImportTexts.clip(code + ": " + message, 512));
        batch.setFinishedAt(LocalDateTime.now()); batch.setUpdatedAt(batch.getFinishedAt());
        requireOne(batches.updateById(batch));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(Long id, Long owner, boolean interrupted, String message) {
        String status = batches.lockStatus(id, owner);
        if (!List.of("RUNNING", "PENDING").contains(status == null ? "" : status)) return;
        summarize(batches.selectById(id), interrupted, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void interruptStale(Long id, Long owner, LocalDateTime cutoff) {
        String status = batches.lockStatus(id, owner);
        if (!List.of("RUNNING", "PENDING").contains(status == null ? "" : status)) return;
        var batch = batches.selectById(id);
        // 锁等待期间可能已有组提交并刷新心跳，必须再次检查，避免误杀正在推进的批次。
        if (batch.getUpdatedAt().isAfter(cutoff)) return;
        summarize(batch, true, "批次30分钟未推进，已中断；核对结果后用新请求键上传，不自动重跑");
    }

    private void summarize(ImportBatchEntity batch, boolean interrupted, String message) {
        var results = groups.selectList(Wrappers.<ImportGroupResultEntity>lambdaQuery().eq(ImportGroupResultEntity::getBatchId, batch.getId()));
        int goodGroups = 0, badGroups = 0, goodRows = 0, badRows = 0;
        for (var result : results) {
            if ("SUCCEEDED".equals(result.getStatus())) { goodGroups++; goodRows += result.getRowCount(); }
            else if ("FAILED".equals(result.getStatus())) { badGroups++; badRows += result.getRowCount(); }
            else throw new IllegalStateException("未知组结果状态");
        }
        int ungrouped = Math.toIntExact(errors.selectCount(Wrappers.<ImportRowErrorEntity>lambdaQuery()
                .eq(ImportRowErrorEntity::getBatchId, batch.getId()).isNull(ImportRowErrorEntity::getGroupResultId)));
        badRows += ungrouped;
        int remaining = batch.getTotalSpuCount() - goodGroups - badGroups;
        if (remaining < 0 || goodRows + badRows > batch.getTotalRowCount()) throw new IllegalStateException("批次统计不一致");
        batch.setSuccessSpuCount(goodGroups); batch.setFailedSpuCount(badGroups); batch.setUnprocessedSpuCount(remaining);
        batch.setSuccessRowCount(goodRows); batch.setFailedRowCount(badRows); batch.setUngroupedFailedRowCount(ungrouped);
        boolean incomplete = remaining > 0 || goodRows + badRows != batch.getTotalRowCount();
        batch.setStatus(interrupted || incomplete ? "INTERRUPTED" : goodGroups == 0 ? "FAILED" : badRows > 0 ? "PARTIAL" : "SUCCEEDED");
        batch.setErrorSummary(ImportTexts.clip(message != null ? message : incomplete ? "持久化结果未覆盖全部数据，请核对" : null, 512));
        batch.setFinishedAt(LocalDateTime.now()); batch.setUpdatedAt(batch.getFinishedAt());
        requireOne(batches.updateById(batch));
    }
}
