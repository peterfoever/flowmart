package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MapStruct 转换器配合 Mock Mapper，验证详情业务，不模拟数据库。 */
class SpuDetailServiceTest {
    private final ProductSpuMapper spus = mock(ProductSpuMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final ProductBrandMapper brands = mock(ProductBrandMapper.class);
    private final SpuServiceImpl service = new SpuServiceImpl(
            Mappers.getMapper(SpuConverter.class), spus, categories, brands, mock(SpuCodeGenerator.class),
            mock(com.flowmart.product.mapper.ProductSkuMapper.class));

    private ProductSpu product() {
        ProductSpu spu = new ProductSpu();
        spu.setId(1L);
        spu.setSpuCode("SPU123");
        spu.setName("测试手机");
        spu.setCategoryId(2L);
        spu.setBrandId(3L);
        spu.setMainImageUrl("main.png");
        spu.setCarouselImages(List.of("front.png", "back.png"));
        SpecDTO spec = new SpecDTO();
        spec.setName("颜色");
        spec.setValues(List.of("黑色", "白色"));
        spu.setSpecs(List.of(spec));
        spu.setDescription("商品说明");
        spu.setStatus(0);
        spu.setDeleted(0L);
        spu.setVersion(5);
        spu.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        spu.setUpdatedAt(LocalDateTime.of(2026, 9, 21, 11, 0));
        when(spus.selectById(1L)).thenReturn(spu);
        return spu;
    }

    private void category() {
        ProductCategory category = new ProductCategory();
        category.setName("手机类目");
        category.setDeleted(0L);
        when(categories.selectById(2L)).thenReturn(category);
    }

    @Test
    void detail_preservesFieldsAndCollectionOrder() {
        ProductSpu spu = product();
        spu.setBrandId(null);
        category();
        var result = service.getDetailById(1L);
        assertAll(
                () -> assertEquals(1L, result.getId()),
                () -> assertEquals("SPU123", result.getSpuCode()),
                () -> assertEquals("测试手机", result.getName()),
                () -> assertEquals(2L, result.getCategoryId()),
                () -> assertEquals("手机类目", result.getCategoryName()),
                () -> assertEquals("main.png", result.getMainImageUrl()),
                () -> assertEquals(spu.getCarouselImages(), result.getCarouselImages()),
                () -> assertEquals("颜色", result.getSpecs().get(0).getName()),
                () -> assertEquals(List.of("黑色", "白色"), result.getSpecs().get(0).getValues()),
                () -> assertEquals("商品说明", result.getDescription()),
                () -> assertEquals(5, result.getVersion()),
                () -> assertEquals(spu.getCreatedAt(), result.getCreatedAt()),
                () -> assertEquals(spu.getUpdatedAt(), result.getUpdatedAt()),
                () -> assertNull(result.getBrandId()),
                () -> assertNull(result.getBrandName()));
        verifyNoInteractions(brands);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void activeOrDisabledBrand_stillReturnsName(int status) {
        product();
        category();
        ProductBrand brand = new ProductBrand();
        brand.setName("品牌A");
        brand.setDeleted(0L);
        brand.setStatus(status);
        when(brands.selectById(3L)).thenReturn(brand);
        var result = service.getDetailById(1L);
        assertEquals(3L, result.getBrandId());
        assertEquals("品牌A", result.getBrandName());
    }

    @ParameterizedTest
    @CsvSource({"0,草稿", "1,上架", "2,下架"})
    void allProductStates_areReadable(int status, String text) {
        product().setStatus(status);
        category();
        var result = service.getDetailById(1L);
        assertEquals(status, result.getStatus());
        assertEquals(text, result.getStatusText());
    }

    @Test
    void emptyCollections_areNotNull() {
        var spu = product();
        spu.setSpecs(List.of());
        spu.setCarouselImages(List.of());
        category();
        var result = service.getDetailById(1L);
        assertEquals(List.of(), result.getSpecs());
        assertEquals(List.of(), result.getCarouselImages());
    }

    @Test
    void absentOrFilteredDeletedSpu_throwsNotFound() {
        // BaseMapper 的逻辑删除条件会将已删除记录过滤为 null；SQL 条件由映射测试覆盖。
        var error = assertThrows(BizException.class, () -> service.getDetailById(1L));
        assertEquals(ProductErrorCode.SPU_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(categories, brands);
    }

    @Test
    void missingBrand_keepsIdAndReturnsNullName() {
        product();
        category();
        var result = service.getDetailById(1L);
        assertEquals(3L, result.getBrandId());
        assertNull(result.getBrandName());
    }

    @Test
    void missingCategory_doesNotHideExistingSpu() {
        product().setBrandId(null);
        var result = assertDoesNotThrow(() -> service.getDetailById(1L));
        assertEquals(1L, result.getId());
        assertEquals(2L, result.getCategoryId());
        assertNull(result.getCategoryName());
    }
}
