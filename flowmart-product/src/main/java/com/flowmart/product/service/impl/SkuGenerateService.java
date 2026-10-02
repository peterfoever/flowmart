package com.flowmart.product.service.impl;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SkuGenerateService {
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final SkuCodeGenerator codeGenerator;
    private final SkuConverter skuConverter;

    private static final int MAX_SKU_PER_SPU = 1000;
    private static final int BATCH_SIZE = 200;

    @Transactional(rollbackFor = Exception.class)
    public List<Long> generate(Long spuId, SkuGenerateDTO dto, Long actor) {
        ProductSpu spu = spuMapper.selectByIdForUpdate(spuId);
        if (spu == null) {
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
        if(StrUtil.trim(dto.getImageUrl()).isEmpty()) {
            dto.setImageUrl(spu.getMainImageUrl());
        }

        // 每个组合构建一个 SKU
        List<Long> rows = new ArrayList<>(combinations.size());
        for (List<SkuSpecValueDTO> combination : combinations) {
            ProductSku sku = skuConverter.toEntity(dto);
            sku.setSkuCode(codeGenerator.next());
            sku.setSpuId(spuId);
            sku.setSpecValues(combination);
            sku.setSpecHash(SpecHashCalculator.calculate(combination));
            sku.setIsDefault(combination.isEmpty());
            skuMapper.insert(sku);
            rows.add(sku.getId());
        }
        return rows;
    }
}
