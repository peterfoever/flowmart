package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.result.PageResult;
import com.flowmart.product.convert.BrandConverter;
import com.flowmart.product.dto.BrandQueryDTO;
import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.dto.UpdateBrandDTO;
import com.flowmart.product.dto.UpdateBrandStatusDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.vo.BrandVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BrandServiceImpl 测试
 */
@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private ProductBrandMapper brandMapper;

    @Mock
    private BrandConverter brandConverter;

    @InjectMocks
    private BrandServiceImpl brandService;

    // ============================================================
    // 创建品牌
    // ============================================================

    @Test
    void createBrand_success() {
        // 准备
        CreateBrandDTO request = new CreateBrandDTO();
        request.setName("华为");
        request.setInitial("H");
        request.setSortNo(1);
        request.setStatus(1);

        ProductBrand entity = new ProductBrand();
        entity.setName("华为");
        entity.setInitial("H");

        when(brandMapper.existsByNameAndDeleted("华为", 0L))
                .thenReturn(false);
        when(brandConverter.toEntity(request)).thenReturn(entity);
        // Mock 不执行 MyBatis-Plus：这里只模拟持久层回填 ID，不验证自动填充插件。
        when(brandMapper.insert(any(ProductBrand.class))).thenAnswer(invocation -> {
            ProductBrand inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });

        // 执行
        Long brandId = brandService.createBrand(request);

        // 验证
        assertEquals(100L, brandId);

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).insert(captor.capture());
        ProductBrand inserted = captor.getValue();

        assertSame(entity, inserted);
        assertEquals(100L, inserted.getId());
        assertEquals("华为", inserted.getName());
        assertEquals("H", inserted.getInitial());
    }

    @Test
    void createBrand_nameDuplicate_throwsException() {
        // 准备
        CreateBrandDTO request = new CreateBrandDTO();
        request.setName("华为");
        request.setInitial("H");

        when(brandMapper.existsByNameAndDeleted("华为", 0L))
                .thenReturn(true);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.createBrand(request)
        );

        assertEquals(ProductErrorCode.BRAND_NAME_DUPLICATE.getCode(),
                exception.getCode());

        // 验证：insert 未被调用
        verify(brandMapper, never()).insert(any(ProductBrand.class));
        verify(brandConverter, never()).toEntity(any(CreateBrandDTO.class));
    }

    @Test
    void createBrand_concurrentDuplicateKey_throwsNameDuplicate() {
        // 准备：并发场景，业务校验通过但唯一索引冲突
        CreateBrandDTO request = new CreateBrandDTO();
        request.setName("华为");
        request.setInitial("H");

        ProductBrand entity = new ProductBrand();
        entity.setName("华为");

        when(brandMapper.existsByNameAndDeleted("华为", 0L))
                .thenReturn(false);
        when(brandConverter.toEntity(request)).thenReturn(entity);
        when(brandMapper.insert(any(ProductBrand.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.createBrand(request)
        );

        assertEquals(ProductErrorCode.BRAND_NAME_DUPLICATE.getCode(),
                exception.getCode());
    }

    @Test
    void createBrand_insertReturnsZero_throwsException() {
        // 准备
        CreateBrandDTO request = new CreateBrandDTO();
        request.setName("华为");
        request.setInitial("H");

        ProductBrand entity = new ProductBrand();

        when(brandMapper.existsByNameAndDeleted("华为", 0L))
                .thenReturn(false);
        when(brandConverter.toEntity(request)).thenReturn(entity);
        when(brandMapper.insert(any(ProductBrand.class))).thenReturn(0);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.createBrand(request)
        );

        assertEquals(ProductErrorCode.BRAND_CREATE_FAILED.getCode(),
                exception.getCode());
    }

    // ============================================================
    // 查询品牌详情
    // ============================================================

    @Test
    void getBrand_success() {
        // 准备
        Long brandId = 1L;
        ProductBrand entity = createBrand(brandId, "华为", 1);
        BrandVO vo = new BrandVO();
        vo.setId(brandId);
        vo.setName("华为");

        when(brandMapper.selectById(brandId)).thenReturn(entity);
        when(brandConverter.toDetailVO(entity)).thenReturn(vo);

        // 执行
        BrandVO result = brandService.getBrand(brandId);

        // 验证
        assertNotNull(result);
        assertEquals(brandId, result.getId());
        assertEquals("华为", result.getName());
        assertEquals("启用", result.getStatusText());
    }

    @Test
    void getBrand_notFound_throwsException() {
        // 准备
        Long brandId = 999L;
        when(brandMapper.selectById(brandId)).thenReturn(null);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.getBrand(brandId)
        );

        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(),
                exception.getCode());

        verify(brandConverter, never()).toDetailVO(any());
    }

    @Test
    void getBrand_disabledStatus_returnsDisabledText() {
        // 准备
        Long brandId = 2L;
        ProductBrand entity = createBrand(brandId, "小米", 0);
        BrandVO vo = new BrandVO();
        vo.setId(brandId);
        vo.setName("小米");

        when(brandMapper.selectById(brandId)).thenReturn(entity);
        when(brandConverter.toDetailVO(entity)).thenReturn(vo);

        // 执行
        BrandVO result = brandService.getBrand(brandId);

        // 验证
        assertEquals("禁用", result.getStatusText());
    }

    @Test
    void getBrand_invalidStatus_returnsNullStatusText() {
        // 准备：数据库中存在非法状态（理论上不应发生）
        Long brandId = 3L;
        ProductBrand entity = createBrand(brandId, "异常品牌", 999);
        BrandVO vo = new BrandVO();
        vo.setId(brandId);
        vo.setName("异常品牌");

        when(brandMapper.selectById(brandId)).thenReturn(entity);
        when(brandConverter.toDetailVO(entity)).thenReturn(vo);

        // 执行
        BrandVO result = brandService.getBrand(brandId);

        // 验证：非法状态返回 null，不默默显示"禁用"
        assertNull(result.getStatusText());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private ProductBrand createBrand(Long id, String name, Integer status) {
        ProductBrand brand = new ProductBrand();
        brand.setId(id);
        brand.setName(name);
        brand.setStatus(status);
        brand.setDeleted(0L);
        return brand;
    }
    // ============================================================
    // 更新品牌
    // ============================================================

    @Test
    void updateBrand_success() {
        // 准备
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("华为技术");
        request.setLogoUrl("https://example.com/new.png");
        request.setInitial("H");
        request.setSortNo(2);

        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        existing.setCreatedAt(createdAt);
        existing.setCreatedBy(42L);

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("华为技术", 1L))
                .thenReturn(false);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(1);

        // 执行
        brandService.updateBrand(brandId, request);

        // 验证
        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).updateById(captor.capture());
        ProductBrand updated = captor.getValue();

        assertEquals(brandId, updated.getId());
        assertEquals("华为技术", updated.getName());
        assertEquals("https://example.com/new.png", updated.getLogoUrl());
        assertEquals("H", updated.getInitial());
        assertEquals(2, updated.getSortNo());
        assertEquals(1, updated.getStatus());
        assertEquals(42L, updated.getCreatedBy());
        assertEquals(createdAt, updated.getCreatedAt());
        assertEquals(0L, updated.getDeleted());
        // Mock 不执行乐观锁插件，只验证传给 Mapper 的原始版本。
        assertEquals(0, updated.getVersion());
    }

    @Test
    void updateBrand_notFound_throwsException() {
        // 准备
        Long brandId = 999L;
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("华为");

        when(brandMapper.selectById(brandId)).thenReturn(null);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.updateBrand(brandId, request)
        );

        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(),
                exception.getCode());

        verify(brandMapper, never()).updateById(any(ProductBrand.class));
    }

    @Test
    void updateBrand_nameDuplicate_throwsException() {
        // 准备
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("小米");  // 与其它品牌重名

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("小米", 1L))
                .thenReturn(true);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.updateBrand(brandId, request)
        );

        assertEquals(ProductErrorCode.BRAND_NAME_DUPLICATE.getCode(),
                exception.getCode());

        verify(brandMapper, never()).updateById(any(ProductBrand.class));
    }

    @Test
    void updateBrand_sameNameExcludeSelf_passes() {
        // 准备：名称未变，排除自身后不冲突
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName(new String("华为"));  // 内容相同、引用不同，模拟 HTTP 与数据库对象。
        request.setLogoUrl("https://example.com/changed.png");
        request.setInitial("H");
        request.setSortNo(1);

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        // 新查询明确排除自身：没有其他品牌占用该名称。
        when(brandMapper.existsByNameExcludingId("华为", brandId)).thenReturn(false);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(1);

        // 执行：不抛异常
        assertDoesNotThrow(() -> brandService.updateBrand(brandId, request));

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).existsByNameExcludingId("华为", brandId);
        verify(brandMapper, never()).existsByNameAndDeleted(anyString(), anyLong());
        verify(brandMapper).updateById(captor.capture());
        assertEquals("华为", captor.getValue().getName());
        assertEquals("https://example.com/changed.png", captor.getValue().getLogoUrl());
    }

    @Test
    void updateBrand_concurrentDuplicateKey_throwsNameDuplicate() {
        // 准备：并发场景，业务校验通过但唯一索引冲突
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("小米");

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("小米", 1L))
                .thenReturn(false);
        when(brandMapper.updateById(any(ProductBrand.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.updateBrand(brandId, request)
        );

        assertEquals(ProductErrorCode.BRAND_NAME_DUPLICATE.getCode(),
                exception.getCode());
    }

    @Test
    void updateBrand_optimisticLockConflict_throwsUpdateFailed() {
        // 准备：更新返回 0 行（version 不匹配）
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 5);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("华为技术");

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("华为技术", 1L))
                .thenReturn(false);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(0);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.updateBrand(brandId, request)
        );

        assertEquals(ProductErrorCode.BRAND_UPDATE_FAILED.getCode(),
                exception.getCode());
    }

    // ============================================================
    // 更新品牌状态
    // ============================================================

    @Test
    void updateStatus_enableToDisable_success() {
        // 准备：启用 → 禁用
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(0);

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(1);

        brandService.updateBrandStatus(brandId, request);

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals(brandId, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals("华为", captor.getValue().getName());
        assertEquals(0, captor.getValue().getVersion());
        verifyNoMoreInteractions(brandMapper);
    }

    @Test
    void updateStatus_disableToEnable_success() {
        // 准备：禁用 → 启用
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 0, 0);
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(1);

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(1);

        brandService.updateBrandStatus(brandId, request);

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals(brandId, captor.getValue().getId());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("华为", captor.getValue().getName());
        assertEquals(0, captor.getValue().getVersion());
    }

    @Test
    void updateStatus_sameStatus_skipsUpdate() {
        // 准备：状态未变化
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(1);  // 已经是 1

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        brandService.updateBrandStatus(brandId, request);

        verify(brandMapper, never()).updateById(any(ProductBrand.class));
        assertEquals(1, existing.getStatus());
    }

    @Test
    void updateStatus_notFound_throwsException() {
        // 准备
        Long brandId = 999L;
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(0);

        when(brandMapper.selectById(brandId)).thenReturn(null);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> brandService.updateBrandStatus(brandId, request)
        );

        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(),
                exception.getCode());

    }

    @Test
    void updateStatus_affectedRowsMismatch_throwsException() {
        // 准备：updateStatus 返回 0
        Long brandId = 1L;
        ProductBrand existing = createBrand(brandId, "华为", 1, 0);
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(0);

        when(brandMapper.selectById(brandId)).thenReturn(existing);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(0);

        BizException exception = assertThrows(BizException.class,
                () -> brandService.updateBrandStatus(brandId, request));

        assertEquals(ProductErrorCode.BRAND_STATUS_CHANGE_FAILED.getCode(), exception.getCode());
        verify(brandMapper).updateById(any(ProductBrand.class));
    }

    @Test
    void updateStatus_alreadyDisabled_skipsUpdate() {
        ProductBrand existing = createBrand(1L, "华为", 0, 5);
        UpdateBrandStatusDTO request = new UpdateBrandStatusDTO();
        request.setStatus(0);
        when(brandMapper.selectById(1L)).thenReturn(existing);

        brandService.updateBrandStatus(1L, request);

        verify(brandMapper, never()).updateById(any(ProductBrand.class));
        assertEquals(0, existing.getStatus());
        assertEquals(5, existing.getVersion());
    }

    @Test
    void updateBrand_databaseUnavailable_preservesOriginalException() {
        ProductBrand existing = createBrand(1L, "华为", 1, 0);
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("华为技术");
        request.setInitial("H");
        request.setSortNo(1);
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("华为技术", 1L)).thenReturn(false);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("测试模拟数据库不可用");
        when(brandMapper.updateById(any(ProductBrand.class))).thenThrow(failure);

        DataAccessResourceFailureException actual = assertThrows(DataAccessResourceFailureException.class,
                () -> brandService.updateBrand(1L, request));

        assertSame(failure, actual);
    }

    @Test
    void updateBrand_emptyLogo_clearsLogoWithoutChangingStatus() {
        ProductBrand existing = createBrand(1L, "旧品牌", 0, 3);
        existing.setLogoUrl("https://example.com/old.png");
        UpdateBrandDTO request = new UpdateBrandDTO();
        request.setName("新品牌");
        request.setLogoUrl("");
        request.setInitial("X");
        request.setSortNo(0);
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.existsByNameExcludingId("新品牌", 1L)).thenReturn(false);
        when(brandMapper.updateById(any(ProductBrand.class))).thenReturn(1);

        brandService.updateBrand(1L, request);

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals("", captor.getValue().getLogoUrl());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getVersion());
    }
    // ============================================================
    // 分页查询
    // ============================================================

    @Test
    void pageBrands_normalPage_returnsRecords() {
        // 准备
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(1);
        query.setSize(20);

        List<ProductBrand> entities = Arrays.asList(
                createBrand(1L, "华为", "H", 1, 1),
                createBrand(2L, "小米", "X", 2, 0)
        );
        List<BrandVO> vos = Arrays.asList(
                createVO(1L, "华为", 1),
                createVO(2L, "小米", 0)
        );

        when(brandMapper.countByQuery(query)).thenReturn(2L);
        when(brandMapper.selectBrandPage(eq(query), eq(0L), eq(20))).thenReturn(entities);
        when(brandConverter.toVOList(entities)).thenReturn(vos);

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals("启用", result.getRecords().get(0).getStatusText());
        assertEquals("禁用", result.getRecords().get(1).getStatusText());
    }

    @Test
    void pageBrands_emptyResult_returnsEmptyPage() {
        // 准备：无匹配数据
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(1);
        query.setSize(20);

        when(brandMapper.countByQuery(query)).thenReturn(0L);

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        // 验证
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());

        // 验证：selectPage 未被调用
        verify(brandMapper, never()).selectBrandPage(any(), anyLong(), anyInt());
    }

    @Test
    void pageBrands_pageBeyondLast_returnsEmptyRecords() {
        // 准备：翻到末页之后
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(999);
        query.setSize(20);

        when(brandMapper.countByQuery(query)).thenReturn(5L);
        when(brandMapper.selectBrandPage(eq(query), eq((999 - 1L) * 20), eq(20)))
                .thenReturn(Collections.emptyList());

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        // 验证
        assertEquals(999, result.getPageNum());
        assertEquals(5L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());

        // 验证：converter 未被调用
        verify(brandConverter, never()).toVOList(anyList());
    }

    @Test
    void pageBrands_withNameFilter_passesFilterToMapper() {
        // 准备
        BrandQueryDTO query = new BrandQueryDTO();
        query.setName("华");
        query.setPage(1);
        query.setSize(10);

        when(brandMapper.countByQuery(query)).thenReturn(1L);
        when(brandMapper.selectBrandPage(eq(query), eq(0L), eq(10)))
                .thenReturn(Collections.singletonList(createBrand(1L, "华为", "H", 1, 1)));
        when(brandConverter.toVOList(anyList()))
                .thenReturn(Collections.singletonList(createVO(1L, "华为", 1)));

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        // 验证
        assertEquals(1L, result.getTotal());
        verify(brandMapper).selectBrandPage(eq(query), eq(0L), eq(10));
    }

    @Test
    void pageBrands_withMultipleFilters_passesAllFilters() {
        // 准备：名称 + 首字母 + 状态
        BrandQueryDTO query = new BrandQueryDTO();
        query.setName("华");
        query.setInitial("H");
        query.setStatus(1);
        query.setPage(1);
        query.setSize(10);

        when(brandMapper.countByQuery(query)).thenReturn(1L);
        when(brandMapper.selectBrandPage(eq(query), eq(0L), eq(10)))
                .thenReturn(Collections.singletonList(createBrand(1L, "华为", "H", 1, 1)));
        when(brandConverter.toVOList(anyList()))
                .thenReturn(Collections.singletonList(createVO(1L, "华为", 1)));

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        // 验证
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(brandMapper).selectBrandPage(eq(query), eq(0L), eq(10));
    }

    @Test
    void pageBrands_offsetCalculationCorrect() {
        // 准备：第 3 页，每页 10 条 → offset = 20
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(3);
        query.setSize(10);

        when(brandMapper.countByQuery(query)).thenReturn(100L);
        when(brandMapper.selectBrandPage(eq(query), eq(20L), eq(10)))
                .thenReturn(Collections.singletonList(createBrand(1L, "华为", "H", 1, 1)));
        when(brandConverter.toVOList(anyList()))
                .thenReturn(Collections.singletonList(createVO(1L, "华为", 1)));

        // 执行
        brandService.pageBrands(query);

        // 验证：offset 计算正确
        verify(brandMapper).selectBrandPage(eq(query), eq(20L), eq(10));
    }

    @Test
    void pageBrands_preservesTotalAndPageSize() {
        // 统一响应提供 total 和 pageSize，由调用方计算总页数。
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(1);
        query.setSize(10);

        when(brandMapper.countByQuery(query)).thenReturn(25L);
        when(brandMapper.selectBrandPage(eq(query), eq(0L), eq(10)))
                .thenReturn(Collections.singletonList(createBrand(1L, "华为", "H", 1, 1)));
        when(brandConverter.toVOList(anyList()))
                .thenReturn(Collections.singletonList(createVO(1L, "华为", 1)));

        // 执行
        PageResult<BrandVO> result = brandService.pageBrands(query);

        assertEquals(25L, result.getTotal());
        assertEquals(10L, result.getPageSize());
        assertEquals(1L, result.getPageNum());
    }

    @Test
    void pageBrands_largePage_doesNotOverflowOffset() {
        BrandQueryDTO query = new BrandQueryDTO();
        query.setPage(Integer.MAX_VALUE);
        query.setSize(100);
        when(brandMapper.countByQuery(query)).thenReturn(5L);
        when(brandMapper.selectBrandPage(query, 214748364600L, 100))
                .thenReturn(Collections.emptyList());

        PageResult<BrandVO> result = brandService.pageBrands(query);

        assertEquals(5L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(brandMapper).selectBrandPage(query, 214748364600L, 100);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private ProductBrand createBrand(Long id, String name, String initial,
                                     Integer sortNo, Integer status) {
        ProductBrand brand = new ProductBrand();
        brand.setId(id);
        brand.setName(name);
        brand.setInitial(initial);
        brand.setSortNo(sortNo);
        brand.setStatus(status);
        brand.setDeleted(0L);
        return brand;
    }

    private BrandVO createVO(Long id, String name, Integer status) {
        BrandVO vo = new BrandVO();
        vo.setId(id);
        vo.setName(name);
        vo.setStatus(status);
        return vo;
    }
    // ============================================================
    // 辅助方法
    // ============================================================

    private ProductBrand createBrand(Long id, String name, Integer status, Integer version) {
        ProductBrand brand = new ProductBrand();
        brand.setId(id);
        brand.setName(name);
        brand.setStatus(status);
        brand.setVersion(version);
        brand.setDeleted(0L);
        return brand;
    }
}
