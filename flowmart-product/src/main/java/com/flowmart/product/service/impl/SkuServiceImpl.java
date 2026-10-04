package com.flowmart.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SkuService;
import com.flowmart.product.vo.SkuListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {
    private final SkuConverter skuConverter;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;

    @Override
    public List<SkuListVO> listSku(Long spuId) {
        ProductSpu spu = productSpuMapper.selectById(spuId);
        if (spu == null || spu.getDeleted() != 0L) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }
        if( productSkuMapper.countActiveBySpuId(spuId) <= 0) {
            return List.of();
        }
        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getSpuId, spuId)
                .orderByAsc(ProductSku::getId));

        //  Entity → VO
        return skus.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
    }
}
