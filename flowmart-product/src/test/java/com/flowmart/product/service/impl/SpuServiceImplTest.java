package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.*;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实转换器，验证保存字段与请求隔离；不模拟数据库事务。 */
class SpuServiceImplTest {
    private final ProductSpuMapper spus = mock(ProductSpuMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final ProductBrandMapper brands = mock(ProductBrandMapper.class);
    private final SpuCodeGenerator codes = mock(SpuCodeGenerator.class);
    private SpuServiceImpl service;
    private CreateSpuDTO request;

    @BeforeEach
    void setUp() {
        service = new SpuServiceImpl(Mappers.getMapper(SpuConverter.class), spus, categories, brands, codes);
        request = new CreateSpuDTO();
        request.setName(" 手机 ");
        request.setCategoryId(1L);
        request.setMainImageUrl(" main.png ");
        request.setCarouselImages(List.of(" a.png ", " b.png "));
        request.setSpecs(List.of(spec(" 颜色 ", " 黑色 ", "白色")));
    }

    private SpecDTO spec(String name, String... values) {
        SpecDTO spec = new SpecDTO();
        spec.setName(name);
        spec.setValues(List.of(values));
        return spec;
    }

    private void validCategory() {
        ProductCategory category = new ProductCategory();
        category.setStatus(1);
        when(categories.selectByIdForUpdate(1L)).thenReturn(category);
        when(categories.isLeafCategory(1L)).thenReturn(true);
    }

    private void successfulInsert() {
        when(codes.next()).thenReturn("SPU123");
        when(spus.insert(any(ProductSpu.class))).thenAnswer(call -> {
            ProductSpu entity = call.getArgument(0);
            entity.setId(100L);
            return 1;
        });
    }

    @Test
    void unbrandedDraft_normalizesWithoutMutatingRequest() {
        validCategory();
        successfulInsert();
        assertEquals(100L, service.createDraft(request));
        var captor = ArgumentCaptor.forClass(ProductSpu.class);
        verify(spus).insert(captor.capture());
        ProductSpu entity = captor.getValue();
        assertEquals("手机", entity.getName());
        assertEquals("main.png", entity.getMainImageUrl());
        assertEquals(List.of("a.png", "b.png"), entity.getCarouselImages());
        assertEquals("颜色", entity.getSpecs().get(0).getName());
        assertEquals(List.of("黑色", "白色"), entity.getSpecs().get(0).getValues());
        assertEquals(0, entity.getStatus());
        assertEquals("SPU123", entity.getSpuCode());
        assertNull(entity.getBrandId());
        assertEquals(" 手机 ", request.getName());
        assertEquals(" 颜色 ", request.getSpecs().get(0).getName());
        assertEquals(" 黑色 ", request.getSpecs().get(0).getValues().get(0));
        verifyNoInteractions(brands);
        verify(categories, never()).existsByCategoryIdAndBrandId(anyLong(), anyLong());
    }

    @Test
    void brandedDraft_locksCategoryThenBrandAndChecksExactBinding() {
        validCategory();
        successfulInsert();
        request.setBrandId(2L);
        ProductBrand brand = new ProductBrand();
        brand.setStatus(1);
        when(brands.selectByIdForUpdate(2L)).thenReturn(brand);
        when(categories.existsByCategoryIdAndBrandId(1L, 2L)).thenReturn(true);
        service.createDraft(request);
        var order = inOrder(categories, brands, spus);
        order.verify(categories).selectByIdForUpdate(1L);
        order.verify(categories).isLeafCategory(1L);
        order.verify(brands).selectByIdForUpdate(2L);
        order.verify(categories).existsByCategoryIdAndBrandId(1L, 2L);
        order.verify(spus).insert(any(ProductSpu.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"images", "main", "names", "values"})
    void normalizedDuplicates_failBeforeDatabaseAccess(String scenario) {
        ProductErrorCode expected;
        switch (scenario) {
            case "images" -> { request.setCarouselImages(List.of("a.png", " a.png ")); expected = ProductErrorCode.IMAGE_DUPLICATE; }
            case "main" -> { request.setCarouselImages(List.of("main.png")); expected = ProductErrorCode.MAIN_IMAGE_IN_CAROUSEL; }
            case "names" -> { request.setSpecs(List.of(spec("颜色", "黑"), spec(" 颜色 ", "白"))); expected = ProductErrorCode.SPEC_NAME_DUPLICATE; }
            default -> { request.setSpecs(List.of(spec("颜色", "黑", " 黑 "))); expected = ProductErrorCode.SPEC_VALUE_DUPLICATE; }
        }
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.createDraft(request)).getCode());
        verifyNoInteractions(categories, brands, spus, codes);
    }

    @Test
    void sameValueAcrossSpecsAndValueEqualToName_areAllowed() {
        validCategory();
        successfulInsert();
        request.setSpecs(List.of(spec("颜色", "颜色", "黑色"), spec("配件颜色", "黑色")));
        assertEquals(100L, service.createDraft(request));
    }

    @Test
    void emptyCollections_arePreserved() {
        validCategory();
        successfulInsert();
        request.setSpecs(List.of());
        request.setCarouselImages(List.of());
        service.createDraft(request);
        var captor = ArgumentCaptor.forClass(ProductSpu.class);
        verify(spus).insert(captor.capture());
        assertEquals(List.of(), captor.getValue().getSpecs());
        assertEquals(List.of(), captor.getValue().getCarouselImages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingCategory", "disabledCategory", "invalidCategory", "nonLeaf", "missingBrand", "disabledBrand", "invalidBrand", "unbound"})
    void invalidReferences_doNotGenerateCodeOrInsert(String scenario) {
        ProductErrorCode expected;
        if (scenario.equals("missingCategory")) {
            expected = ProductErrorCode.CATEGORY_NOT_FOUND;
        } else if (scenario.endsWith("Category")) {
            ProductCategory category = new ProductCategory();
            category.setStatus(scenario.equals("disabledCategory") ? 0 : 9);
            when(categories.selectByIdForUpdate(1L)).thenReturn(category);
            expected = ProductErrorCode.CATEGORY_DISABLED;
        } else if (scenario.equals("nonLeaf")) {
            ProductCategory category = new ProductCategory();
            category.setStatus(1);
            when(categories.selectByIdForUpdate(1L)).thenReturn(category);
            expected = ProductErrorCode.CATEGORY_NOT_LEAF;
        } else {
            validCategory();
            request.setBrandId(2L);
            expected = ProductErrorCode.BRAND_NOT_FOUND;
            if (!scenario.equals("missingBrand")) {
                ProductBrand brand = new ProductBrand();
                brand.setStatus(scenario.equals("unbound") ? 1 : scenario.equals("disabledBrand") ? 0 : 9);
                when(brands.selectByIdForUpdate(2L)).thenReturn(brand);
                expected = scenario.equals("unbound") ? ProductErrorCode.CATEGORY_BRAND_NOT_BOUND : ProductErrorCode.BRAND_DISABLED;
            }
        }
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.createDraft(request)).getCode());
        verifyNoInteractions(spus, codes);
    }

    @Test
    void zeroRows_failsCreation() {
        validCategory();
        when(codes.next()).thenReturn("SPU123");
        assertEquals(ProductErrorCode.SPU_CREATE_FAILED.getCode(),
                assertThrows(BizException.class, () -> service.createDraft(request)).getCode());
    }

    @Test
    void databaseException_isNotSwallowed() {
        validCategory();
        when(codes.next()).thenReturn("SPU123");
        var failure = new DuplicateKeyException("模拟编码冲突");
        when(spus.insert(any(ProductSpu.class))).thenThrow(failure);
        assertSame(failure, assertThrows(DuplicateKeyException.class, () -> service.createDraft(request)));
    }
}
