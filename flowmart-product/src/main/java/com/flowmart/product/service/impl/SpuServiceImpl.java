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
import com.flowmart.product.enums.SpuStatus;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SpuService;
import com.flowmart.product.vo.SpuDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
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

    private static final int STATUS_DRAFT = 0;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long createDraft(CreateSpuDTO request) {
        log.info("创建SPU草稿请求: name={}, categoryId={}, brandId={}",
                request.getName(), request.getCategoryId(), request.getBrandId());
        // ========== Step 1: 规范化并校验图片、规格 ==========
        ProductSpu entity = converter.toEntity(request);
        entity.setName(StrUtil.trim(entity.getName()));
        entity.setMainImageUrl(StrUtil.trim(entity.getMainImageUrl()));
        normalizeAndValidateImages(entity);
        normalizeAndValidateSpecs(entity);

        // 锁类目并校验
        ProductCategory productCategory = categoryMapper.selectByIdForUpdate(entity.getCategoryId());
        if (productCategory == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!CategoryStatus.ENABLED.matches(productCategory.getStatus())) {
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
            if (!BrandStatus.ENABLED.matches(productBrand.getStatus())) {
                throw new BizException(ProductErrorCode.BRAND_DISABLED);
            }
            if (!categoryMapper.existsByCategoryIdAndBrandId(entity.getCategoryId(), entity.getBrandId())) {
                throw new BizException(ProductErrorCode.CATEGORY_BRAND_NOT_BOUND);
            }
        }

        // 设置后端字段并插入
        entity.setSpuCode(codeGenerator.next());
        entity.setStatus(STATUS_DRAFT);
        int insert = spuMapper.insert(entity);
        if (insert != 1) {
            throw new BizException(ProductErrorCode.SPU_CREATE_FAILED
            ,"插入spu失败，期望影响1行，实际影响" + insert + "行");
        }
        log.info("创建spu成功，id={},spuCode={},categoryId={}",entity.getId(),entity.getSpuCode(),entity.getCategoryId());
        return entity.getId();
    }

    @Override
    public SpuDetailVO getDetailById(Long id) {
        log.debug("查询SPU详情: id={}", id);
        ProductSpu productSpu = spuMapper.selectById(id);
        if (productSpu == null) {
            throw new BizException(ProductErrorCode.SPU_NOT_FOUND);
        }
        SpuDetailVO spuDetailVO = converter.toDetailVO(productSpu);
        if(productSpu.getBrandId() != null) {
            ProductBrand brand = brandMapper.selectById(productSpu.getBrandId());
            if (brand != null && brand.getDeleted() != 0L) {
                spuDetailVO.setBrandName(brand.getName());
            }
        }
        spuDetailVO.setCategoryName(categoryMapper.selectById(productSpu.getCategoryId()).getName());
        spuDetailVO.setStatusText(SpuStatus.getDescByCode(spuDetailVO.getStatus()));
        log.debug("查询SPU详情成功: id={}, spuCode={}, status={}",
                id, productSpu.getSpuCode(), productSpu.getStatus());
        return spuDetailVO;
    }

    // 校验轮播图
    private void normalizeAndValidateImages(ProductSpu entity) {
        List<String> normalized = entity.getCarouselImages().stream().map(StrUtil::trim).toList();
        Set<String> seenImages = new HashSet<>();
        for (String image : normalized) {
            if (!seenImages.add(image)) {
                throw new BizException(ProductErrorCode.IMAGE_DUPLICATE);
            }
            if (image.equals(entity.getMainImageUrl())) {
                throw new BizException(ProductErrorCode.MAIN_IMAGE_IN_CAROUSEL);
            }
        }
        entity.setCarouselImages(normalized);
    }

    // 校验规格
    private void normalizeAndValidateSpecs(ProductSpu entity) {
        Set<String> seenSpecNames = new HashSet<>();
        List<SpecDTO> normalized = new ArrayList<>();
        for (SpecDTO source : entity.getSpecs()) {
            // 单独创建规格对象，避免修改 MapStruct 浅拷贝所共享的请求对象。
            SpecDTO spec = new SpecDTO();
            spec.setName(StrUtil.trim(source.getName()));
            if (!seenSpecNames.add(spec.getName())) {
                throw new BizException(ProductErrorCode.SPEC_NAME_DUPLICATE);
            }
            List<String> values = source.getValues().stream().map(StrUtil::trim).toList();
            Set<String> seenValues = new HashSet<>();
            for (String value : values) {
                if (!seenValues.add(value)) {
                    throw new BizException(ProductErrorCode.SPEC_VALUE_DUPLICATE);
                }
            }
            spec.setValues(values);
            normalized.add(spec);
        }
        entity.setSpecs(normalized);
    }
}
