package com.flowmart.product.service.impl;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.BrandStatus;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

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
        ProductSpu entity = converter.toEntity(request);
        StrUtil.trim(entity.getName());
        StrUtil.trim(entity.getMainImageUrl());
        for (String carouselImage : entity.getCarouselImages()) {
            StrUtil.trim(carouselImage);
        }
        for (SpecDTO spec : entity.getSpecs()) {
            StrUtil.trim(spec.getName());
            for (String value : spec.getValues()) {
                StrUtil.trim(value);
            }
        }
        normalizeAndValidateImages(entity);
        normalizeAndValidateSpecs(entity);

        // 锁类目并校验
        ProductCategory productCategory = categoryMapper.selectByIdForUpdate(entity.getCategoryId());
        if (productCategory == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        if (CategoryStatus.DISABLED.matches(productCategory.getStatus())) {
            throw new BizException(ProductErrorCode.CATEGORY_DISABLED);
        }
        if(!categoryMapper.isLeafCategory(entity.getCategoryId())) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_LEAF);
        }

        // 有品牌才进行品牌校验
        if (entity.getBrandId() != null) {
            ProductBrand productBrand = brandMapper.selectByIdForUpdate(entity.getBrandId());
            if (productBrand == null) {
                throw new BizException(ProductErrorCode.BRAND_NOT_FOUND);
            }
            if (BrandStatus.DISABLED.matches(productBrand.getStatus())) {
                throw new BizException(ProductErrorCode.BRAND_DISABLED);
            }
            if (!categoryMapper.existsByCategoryIdAndBrandId(entity.getCategoryId(), entity.getBrandId())) {
                throw new BizException(ProductErrorCode.CATEGORY_BRAND_NOT_BOUND);
            }
        }

        // 设置后端字段并插入
        entity.setSpuCode(codeGenerator.next());
        int insert = spuMapper.insert(entity);
        if (insert != 1) {
            throw new BizException(ProductErrorCode.SPU_CREATE_FAILED
            ,"插入spu失败，期望影响1行，实际影响" + insert + "行");
        }
        log.info("创建spu成功，id={},spuCode={},categoryId={}",entity.getId(),entity.getSpuCode(),entity.getCategoryId());
        return entity.getId();
    }

    // 校验轮播图
    private void normalizeAndValidateImages(ProductSpu entity) {
        Set<String> seenImages = new HashSet<>();
        for (int i = 0; i < entity.getCarouselImages().size(); i++) {
            if (!seenImages.add(entity.getCarouselImages().get(i))) {
                throw new BizException(ProductErrorCode.IMAGE_DUPLICATE);
            }
            if (entity.getCarouselImages().get(i).equals(entity.getMainImageUrl())) {
                throw new BizException(ProductErrorCode.MAIN_IMAGE_IN_CAROUSEL);
            }
        }
    }

    // 校验规格
    private void normalizeAndValidateSpecs(ProductSpu entity) {
        Set<String> seenSpecs = new HashSet<>();
        for (int i = 0; i < entity.getSpecs().size(); i++) {
            if (!seenSpecs.add(entity.getSpecs().get(i).getName())) {
                throw new BizException(ProductErrorCode.SPEC_NAME_DUPLICATE);
            }
            Set<String> specs = new HashSet<>();
            for (int j = 0; j < entity.getSpecs().get(i).getValues().size(); j++) {
                if (!seenSpecs.add(entity.getSpecs().get(i).getValues().get(j))) {
                    throw new BizException(ProductErrorCode.SPEC_VALUE_DUPLICATE);
                }
            }
        }
    }
}
