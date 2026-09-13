package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.BrandConverter;
import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.enums.BrandStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.service.BrandService;
import com.flowmart.product.vo.BrandVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BrandServiceImpl implements BrandService {
    @Autowired
    private ProductBrandMapper mapper;

    @Autowired
    private BrandConverter converter;

    /**
     * 系统操作人 ID，后续接入登录上下文后替换
     */
    private static final Long SYSTEM_OPERATOR_ID = 0L;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long createBrand(CreateBrandDTO request) {
        log.info("创建品牌请求: name={}, initial={}", request.getName(), request.getInitial());
        boolean exists = mapper.existsByNameAndDeleted(request.getName(), 0L);
        if (exists) {
            throw new BizException(ProductErrorCode.BRAND_NAME_DUPLICATE);
        }
        ProductBrand entity = converter.toEntity(request);
        try {
            int rows = mapper.insert(entity);
            if (rows != 1) {
                throw new BizException(ProductErrorCode.BRAND_CREATE_FAILED,
                        "插入品牌失败，期望影响1行，实际影响" + rows + "行");
            }
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一索引 uk_name_deleted 触发
            log.warn("并发创建同名品牌冲突: name={}", request.getName(), e);
            throw new BizException(ProductErrorCode.BRAND_NAME_DUPLICATE);
        }
        log.info("创建品牌成功: brandId={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    public BrandVO getBrand(Long id) {
        log.debug("查询品牌详情: id={}", id);
        ProductBrand productBrand = mapper.selectById(id);
        if (productBrand == null || productBrand.getDeleted() != 0) {
            throw new BizException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        BrandVO brandVO = converter.toDetailVO(productBrand);
        brandVO.setStatusText(BrandStatus.getDescByCode(productBrand.getStatus()));
        log.debug("查询品牌详情成功: id={}, name={}", id, productBrand.getName());
        return brandVO;
    }

}
