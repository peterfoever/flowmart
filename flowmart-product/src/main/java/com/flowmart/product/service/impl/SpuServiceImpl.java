package com.flowmart.product.service.impl;

import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.entity.SpuCodeGenerator;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {
    private final SpuConverter converter;
    private final ProductSpuMapper spuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final SpuCodeGenerator codeGenerator;

    private static final Long SYSTEM_OPERATOR_ID = 0L;
    private static final int STATUS_DRAFT = 0;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long createDraft(CreateSpuDTO request) {
        log.info("创建SPU草稿请求: name={}, categoryId={}, brandId={}",
                request.getName(), request.getCategoryId(), request.getBrandId());
        // ========== Step 1: 规范化并校验图片、规格 ==========

        return 0L;
    }
}
