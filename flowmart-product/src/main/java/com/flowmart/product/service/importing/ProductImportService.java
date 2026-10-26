package com.flowmart.product.service.importing;

import com.flowmart.common.exception.*;
import com.flowmart.product.convert.ImportConverter;
import com.flowmart.product.entity.ImportBatchEntity;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ImportBatchMapper;
import com.flowmart.product.support.*;
import com.flowmart.product.vo.imports.ImportSubmissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.security.*;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImportService {
    private final ImportBatchMapper batches;
    private final ImportBatchTransactions transactions;
    private final ImportGroupWriter writer;
    private final ImportFailureWriter failures;
    private final ImportPrechecker prechecker;
    private final ImportFileStorage storage;
    private final ImportExcelReader reader;
    private final ImportConverter converter;

    /** 同步、有界导入。owner/actor只能由可信服务端上下文提供，不能来自上传表格或请求参数。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImportSubmissionVO submit(Long owner, Long actor, String key, byte[] bytes, String filename, String mime) {
        if (owner == null || owner < 0 || actor == null || actor < 0 || key == null || key.isBlank()
                || key.length() > 64 || bytes == null || filename == null || filename.isBlank()) throw new BizException(CommonErrorCode.PARAM_INVALID);
        if (bytes.length > ImportExcelReader.MAX_BYTES) throw new BizException(ProductErrorCode.IMPORT_FILE_INVALID);
        String hash = sha256(bytes);
        // MIME与扩展名决定文件是否接受，必须纳入指纹；文件名的展示部分不决定业务内容。
        String options = "{\"xlsx\":" + filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                + ",\"correctMime\":" + ImportExcelReader.MIME.equals(mime) + "}";
        String fingerprint = ImportRequestFingerprint.calculate(hash, ImportExcelReader.VERSION, options);
        var existing = byKey(owner, key);
        if (existing != null) return replay(existing, fingerprint);
        ImportBatchEntity batch;
        try {
            batch = transactions.create(owner, actor, key, fingerprint, filename, hash);
        } catch (DuplicateKeyException e) {
            if (!ImportSqlErrors.duplicateOn(e, "uk_import_batch_idem")) throw e;
            existing = byKey(owner, key); // 创建事务已经回滚；此时读取唯一键竞争赢家已提交的批次。
            if (existing == null) throw e;
            return replay(existing, fingerprint);
        }
        try {
            String path = storage.save(batch.getId(), bytes);
            var parsed = reader.read(bytes, filename, mime);
            if (parsed.hasFileLevelError()) {
                transactions.rejectFile(batch.getId(), owner, path, parsed.fileLevelError().errorCode(), parsed.fileLevelError().errorMessage());
            } else {
                var grouped = new ImportRowGrouper().group(parsed.rows(), parsed.rowErrors());
                transactions.start(batch.getId(), owner, grouped.groups().size() + grouped.invalidGroups().size(),
                        parsed.rows().size() + parsed.rowErrors().size(), path);
                var rejected = prechecker.check(owner, grouped.groups());
                if (!grouped.ungroupedRows().isEmpty()) failures.ungrouped(batch.getId(), owner, grouped.ungroupedRows());
                for (var group : grouped.invalidGroups()) failures.invalid(batch.getId(), owner, actor, group);
                for (var group : grouped.groups()) {
                    var failure = rejected.get(group.importItemKeyNormalized());
                    if (failure != null) {
                        failures.rejected(batch.getId(), owner, actor, group, failure);
                        continue;
                    }
                    try {
                        writer.write(batch.getId(), owner, actor, group);
                    } catch (ImportGroupRejectedException e) {
                        failures.rejected(batch.getId(), owner, actor, group, e);
                    }
                }
                transactions.finish(batch.getId(), owner, false, null);
            }
        } catch (Exception e) {
            // 提交异常也可能意味着已提交，绝不伪造失败组；按已持久化结果汇总并中止。
            log.error("商品导入中断: batchId={}, ownerId={}", batch.getId(), owner, e);
            try {
                transactions.finish(batch.getId(), owner, true, "系统异常，导入已中断；请核对已持久化结果");
            } catch (Exception summarizeError) {
                log.error("导入批次收尾失败，等待超时核对: batchId={}", batch.getId(), summarizeError);
                throw new BizException(ProductErrorCode.IMPORT_ABORTED, "批次" + batch.getId() + "中断且收尾失败，请查询核对");
            }
        }
        var result = batches.selectById(batch.getId());
        log.info("商品导入结束: batchId={}, status={}", batch.getId(), result.getStatus());
        return new ImportSubmissionVO(converter.toBatch(result), false);
    }

    private ImportBatchEntity byKey(Long owner, String key) {
        return transactions.findByKey(owner, key);
    }
    private ImportSubmissionVO replay(ImportBatchEntity existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) throw new BizException(ProductErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        return new ImportSubmissionVO(converter.toBatch(existing), true);
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
