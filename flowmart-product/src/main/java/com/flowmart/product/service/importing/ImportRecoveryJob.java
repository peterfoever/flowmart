package com.flowmart.product.service.importing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.product.entity.ImportBatchEntity;
import com.flowmart.product.mapper.ImportBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import java.time.LocalDateTime;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowmart.imports.recovery-enabled", havingValue = "true", matchIfMissing = true)
public class ImportRecoveryJob {
    private final ImportBatchMapper batches;
    private final ImportBatchTransactions transactions;
    @Scheduled(fixedDelayString = "${flowmart.imports.recovery-delay-ms:60000}")
    public void recover() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        var stale = batches.selectList(Wrappers.<ImportBatchEntity>lambdaQuery().in(ImportBatchEntity::getStatus, "PENDING", "RUNNING")
                .le(ImportBatchEntity::getUpdatedAt, cutoff).orderByAsc(ImportBatchEntity::getUpdatedAt).last("LIMIT 100"));
        for (var batch : stale) {
            try { transactions.interruptStale(batch.getId(), batch.getOwnerId(), cutoff); }
            catch (Exception e) { log.error("导入超时核对失败: batchId={}", batch.getId(), e); }
        }
    }
}
