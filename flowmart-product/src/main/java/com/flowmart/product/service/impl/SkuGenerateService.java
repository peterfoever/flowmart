package com.flowmart.product.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.enums.SpuStatus;
import com.flowmart.product.generator.SkuCodeGenerator;
import com.flowmart.product.generator.SpecCombinationGenerator;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SkuGenerateService {
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final SkuCodeGenerator codeGenerator;
    private final SkuConverter skuConverter;

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final int BATCH_SIZE = 200;

    /**
     * 为草稿 SPU 初始化 SKU。与 SPU 修改共享行锁，所有插入批次属于同一事务。
     * @param actor 由服务端传入的操作人，0 为系统账号，不允许客户端指定
     * @return 按规格展开顺序排列的 SKU ID
     * @throws BizException 参数非法、非草稿、已生成或批量写入结果异常
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generate(Long spuId, SkuGenerateDTO dto, Long actor) {
        validateRequest(spuId, dto, actor);
        ProductSpu spu = spuMapper.selectByIdForUpdate(spuId);
        if (spu == null || !Long.valueOf(0L).equals(spu.getDeleted())) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }
        if (!SpuStatus.DRAFT.matches(spu.getStatus())) {
            throw new BizException(ProductErrorCode.SPU_NOT_DRAFT);
        }

        // 检查是否已经生成
        if (skuMapper.countActiveBySpuId(spuId) > 0) {
            throw new BizException(ProductErrorCode.SKU_ALREADY_GENERATED);
        }

        // 生成组合，确定图片
        var combinations = SpecCombinationGenerator.generate(spu.getSpecs());
        String imageUrl = StrUtil.trim(dto.getImageUrl());
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = StrUtil.trim(spu.getMainImageUrl());
        }
        if (StrUtil.isBlank(imageUrl) || imageUrl.length() > 512) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "SKU图片不能为空且长度不能超过512个字符");
        }

        // 自定义多行 INSERT 不依赖 BaseMapper 的逐条 ID/审计填充。
        LocalDateTime now = LocalDateTime.now();
        List<ProductSku> skus = new ArrayList<>(combinations.size());
        for (List<SkuSpecValueDTO> combination : combinations) {
            ProductSku sku = skuConverter.toEntity(dto);
            sku.setId(IdWorker.getId());
            sku.setSkuCode(codeGenerator.next());
            sku.setSpuId(spuId);
            sku.setSpecValues(combination);
            sku.setSpecHash(SpecHashCalculator.calculate(combination));
            sku.setIsDefault(combination.isEmpty());
            sku.setImageUrl(imageUrl);
            sku.setDeleted(0L);
            sku.setVersion(0);
            sku.setCreatedBy(actor);
            sku.setUpdatedBy(actor);
            sku.setCreatedAt(now);
            sku.setUpdatedAt(now);
            skus.add(sku);
        }
        try {
            for (int start = 0; start < skus.size(); start += BATCH_SIZE) {
                List<ProductSku> batch = skus.subList(start, Math.min(start + BATCH_SIZE, skus.size()));
                if (skuMapper.batchInsert(batch) != batch.size()) {
                    throw new BizException(ProductErrorCode.SKU_GENERATE_FAILED, "SKU批量插入行数异常，生成已中止");
                }
            }
        } catch (DuplicateKeyException e) {
            log.warn("生成SKU发生唯一约束冲突: spuId={}", spuId, e);
            BizException failure = new BizException(ProductErrorCode.SKU_DATA_CONFLICT);
            failure.initCause(e);
            throw failure;
        }
        log.info("生成SKU执行完成: spuId={}, count={}, actor={}", spuId, skus.size(), actor);
        return skus.stream().map(ProductSku::getId).toList();
    }

    private void validateRequest(Long spuId, SkuGenerateDTO dto, Long actor) {
        if (spuId == null || spuId <= 0 || dto == null || actor == null || actor < 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        BigDecimal price = dto.getPrice();
        if (price == null || price.signum() < 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2
                || (dto.getImageUrl() != null && dto.getImageUrl().length() > 512)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }
}
