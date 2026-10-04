package com.flowmart.product.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuUpdateDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.enums.SpuStatus;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SkuService;
import com.flowmart.product.vo.SkuDetailVO;
import com.flowmart.product.vo.SkuListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {
    private final SkuConverter skuConverter;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;

    /**
     * 本期操作人固定 0L，由服务端提供，不接受客户端传入。
     */
    private static final long SYSTEM_ACTOR = 0L;

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");


    @Override
    public List<SkuListVO> listSku(Long spuId) {
        ProductSpu spu = productSpuMapper.selectById(spuId);
        if (spu == null || spu.getDeleted() != 0L) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }

        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getSpuId, spuId)
                .orderByAsc(ProductSku::getId));

        if (skus.isEmpty()) {
            return List.of();
        }
        //  Entity → VO
        return skus.stream()
                .map(skuConverter::toListVO)
                .collect(Collectors.toList());
    }

    @Override
    public SkuDetailVO detailSku(Long id) {
        ProductSku productSku = productSkuMapper.selectById(id);
        if (productSku == null) {
            throw new BizException(ProductErrorCode.SKU_NOT_FOUND);
        }
        ProductSpu spu = productSpuMapper.selectById(productSku.getSpuId());
        if (spu == null || spu.getDeleted() != 0L) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }
        return skuConverter.toDetailVO(productSku);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSku(Long skuId, SkuUpdateDTO request) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ProductErrorCode.SKU_NOT_FOUND);
        }
        ProductSpu spu = productSpuMapper.selectByIdForUpdate(sku.getSpuId());
        if (spu == null || spu.getDeleted() != 0L) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }

        if (!SpuStatus.DRAFT.matches(spu.getStatus())) {
            throw new BizException(ProductErrorCode.SPU_NOT_DRAFT);
        }

        // 执行校验
        String imageUrl = StrUtil.trim(request.getImageUrl());
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = spu.getMainImageUrl();
        }
        BigDecimal price = request.getPrice();
        if (price == null || price.signum() < 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2
                || (request.getImageUrl() != null && request.getImageUrl().length() > 512)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }

        // 执行更新操作
        int affected = productSkuMapper.updatePriceAndImage(skuId, spu.getId(),
                request.getVersion(), request.getPrice(), request.getImageUrl(),
                SYSTEM_ACTOR, LocalDateTime.now());

        if (affected == 0) {
            // 行存在但版本不匹配，或者 spu_id 不一致
            // 再查一次区分"不存在"与"版本冲突"
            ProductSku latest = productSkuMapper.selectById(skuId);
            if (latest == null || !Objects.equals(latest.getSpuId(), spu.getId())) {
                throw new BizException(ProductErrorCode.SKU_NOT_FOUND);
            }
            throw new BizException(ProductErrorCode.SKU_VERSION_CONFLICT);
        }
        if (affected != 1) {
            // 理论上不会发生：主键更新影响行数应为 1
            throw new BizException(ProductErrorCode.SKU_UPDATE_FAILED);
        }
    }
}
