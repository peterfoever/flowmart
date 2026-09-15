package com.flowmart.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.result.PageResult;
import com.flowmart.product.convert.BrandConverter;
import com.flowmart.product.dto.BrandQueryDTO;
import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.dto.UpdateBrandDTO;
import com.flowmart.product.dto.UpdateBrandStatusDTO;
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

import java.util.Collections;
import java.util.List;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrand(Long id, UpdateBrandDTO request) {
        log.info("更新品牌请求: id={}, name={}", id, request.getName());
        ProductBrand productBrand = mapper.selectById(id);
        if (productBrand == null || productBrand.getDeleted() != 0) {
            throw new BizException(ProductErrorCode.BRAND_NOT_FOUND);
        }

        // 由数据库按相同排序规则判重，并排除当前品牌，避免将自己判断为重名。
        if (mapper.existsByNameExcludingId(request.getName(), id)) {
            throw new BizException(ProductErrorCode.BRAND_NAME_DUPLICATE);
        }
        productBrand.setName(request.getName());
        productBrand.setLogoUrl(request.getLogoUrl());
        productBrand.setInitial(request.getInitial());
        productBrand.setSortNo(request.getSortNo());
        int updated;
        try {
            updated = mapper.updateById(productBrand);
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一索引 uk_name_deleted 触发
            log.warn("并发更新品牌名称冲突: id={}, name={}", id, request.getName(), e);
            throw new BizException(ProductErrorCode.BRAND_NAME_DUPLICATE);
        }

        if (updated != 1) {
            throw new BizException(ProductErrorCode.BRAND_UPDATE_FAILED);
        }
        log.info("更新品牌成功: id={}, name={}", id, request.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrandStatus(Long id, UpdateBrandStatusDTO request) {
        log.info("更新品牌状态请求: id={}, status={}", id, request.getStatus());

        ProductBrand productBrand = mapper.selectById(id);
        if (productBrand == null || productBrand.getDeleted() != 0) {
            throw new BizException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        if (BrandStatus.ENABLED.matches(request.getStatus())
                && BrandStatus.ENABLED.matches(productBrand.getStatus())) {
            log.info("品牌状态未变化，跳过更新: id={}, status={}", id, request.getStatus());
            return;
        }
        if (BrandStatus.DISABLED.matches(request.getStatus())
                && BrandStatus.DISABLED.matches(productBrand.getStatus())) {
            log.info("品牌状态未变化，跳过更新: id={}, status={}", id, request.getStatus());
            return;
        }
        productBrand.setStatus(request.getStatus());
        int updated = mapper.updateById(productBrand);
        if (updated != 1) {
            throw new BizException(ProductErrorCode.BRAND_STATUS_CHANGE_FAILED);
        }
        log.info("更新品牌状态成功: id={}, newStatus={}",
                id, request.getStatus());
    }

    @Override
    public PageResult<BrandVO> pageBrands(BrandQueryDTO query) {
        log.debug("分页查询品牌：name={},initial={},status={},page={},size={}",
                query.getName(), query.getInitial(), query.getStatus(), query.getPage(), query.getSize());

        // ========== Step 1: 统计总数 ==========
        long count = mapper.countByQuery(query);
        if (count == 0) {
            // 空结果，直接返回
            return PageResult.empty(query.getPage(), query.getSize());
        }
        // ========== Step 2: 计算偏移量 ==========
        long offset = (query.getPage() - 1L) * query.getSize();
        // ========== Step 3: 分页查询 ==========
        List<ProductBrand> productBrands = mapper.selectBrandPage(query, offset, query.getSize());

        if (CollectionUtils.isEmpty(productBrands)) {
            // 翻到末页之后
            return new PageResult<>(query.getPage(), query.getSize(), count, Collections.emptyList());
        }
        // ========== Step 4: Entity → VO ==========
        List<BrandVO> records = converter.toVOList(productBrands);

        // ========== Step 5: 填充派生字段 statusText ==========
        for (BrandVO vo : records) {
            vo.setStatusText(BrandStatus.getDescByCode(vo.getStatus()));
        }

        // ========== Step 6: 构建分页结果 ==========
        return new PageResult<>(query.getPage(), query.getSize(), count, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBrand(Long id) {
        log.info("删除品牌请求: id={}", id);
        ProductBrand productBrand = mapper.selectByIdForUpdate(id);
        if (productBrand == null || productBrand.getDeleted() != 0) {
            throw new BizException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        if (mapper.existCategoryBindings(id)) {
            throw new BizException(ProductErrorCode.BRAND_BOUND_BY_CATEGORY);
        }
        int delete = mapper.logicDeleteById(id, productBrand.getVersion(), SYSTEM_OPERATOR_ID);
        if (delete != 1) {
            throw new BizException(ProductErrorCode.BRAND_DELETE_FAILED);
        }
        log.info("删除品牌成功: id={}, name={}, version={}",
                id, productBrand.getName(), productBrand.getVersion());

    }
}
