package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.BrandConverter;
import com.flowmart.product.dto.CreateBrandDTO;
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

import java.time.LocalDateTime;

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

    private static final Long OPERATOR_ID = 0L;

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
        when(brandMapper.insert(any(ProductBrand.class))).thenReturn(1);

        // 执行
        Long brandId = brandService.createBrand(request);

        // 验证
        assertNotNull(brandId);

        ArgumentCaptor<ProductBrand> captor = ArgumentCaptor.forClass(ProductBrand.class);
        verify(brandMapper).insert(captor.capture());
        ProductBrand inserted = captor.getValue();

        // 验证审计字段和默认值被填充
        assertNotNull(inserted.getId());
        assertEquals(OPERATOR_ID, inserted.getCreatedBy());
        assertNotNull(inserted.getCreatedAt());
        assertEquals(OPERATOR_ID, inserted.getUpdatedBy());
        assertNotNull(inserted.getUpdatedAt());
        assertEquals(0L, inserted.getDeleted());
        assertEquals(0, inserted.getVersion());
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
        verify(brandConverter, never()).toEntity(any());
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
}
