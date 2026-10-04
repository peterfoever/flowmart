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
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实转换器验证更新字段，Mapper mock 不证明数据库提交或回滚。 */
class SpuUpdateServiceTest {
    private final ProductSpuMapper mapper = mock(ProductSpuMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final ProductBrandMapper brands = mock(ProductBrandMapper.class);
    private final SpuCodeGenerator generator = mock(SpuCodeGenerator.class);
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final SpuServiceImpl service = new SpuServiceImpl(Mappers.getMapper(SpuConverter.class), mapper, categories, brands, generator, skus);
    private ProductSpu existing;
    private UpdateSpuDTO request;

    @BeforeEach
    void setup() {
        existing = new ProductSpu();
        existing.setId(1L);
        existing.setSpuCode("SPU001");
        existing.setCategoryId(2L);
        existing.setBrandId(3L);
        existing.setDeleted(0L);
        existing.setStatus(0);
        existing.setSpecs(List.of());
        existing.setVersion(Integer.valueOf("128"));
        existing.setCreatedBy(42L);
        existing.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        request = new UpdateSpuDTO();
        request.setName(" 新名称 ");
        request.setCategoryId(2L);
        request.setBrandId(3L);
        request.setVersion(Integer.valueOf("128"));
        request.setMainImageUrl(" main.png ");
        request.setCarouselImages(List.of());
        request.setSpecs(List.of());
        when(mapper.selectByIdForUpdate(1L)).thenReturn(existing);
        when(mapper.updateDraftById(anyLong(), anyInt(), anyString(), anyLong(), nullable(Long.class),
                anyString(), anyList(), anyList(), nullable(String.class), anyLong())).thenReturn(1);
    }

    private void category(long id) {
        ProductCategory category = new ProductCategory();
        category.setStatus(1);
        when(categories.selectByIdForUpdate(id)).thenReturn(category);
        when(categories.isLeafCategory(id)).thenReturn(true);
    }

    private void brand(long id, int status) {
        ProductBrand brand = new ProductBrand();
        brand.setStatus(status);
        when(brands.selectByIdForUpdate(id)).thenReturn(brand);
    }

    private void assertNoUpdate() {
        verify(mapper, never()).updateDraftById(anyLong(), anyInt(), any(), any(), any(), any(), anyList(), anyList(), any(), any());
    }

    @Test
    void sameOwnership_usesValueVersionAndPreservesProtectedFields() {
        service.updateDraft(1L, request);
        verify(mapper).updateDraftById(1L, 128, "新名称", 2L, 3L, "main.png", List.of(), List.of(), null, 0L);
        assertEquals("SPU001", existing.getSpuCode());
        assertEquals(0, existing.getStatus());
        assertEquals(42L, existing.getCreatedBy());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), existing.getCreatedAt());
        assertEquals(128, existing.getVersion());
        assertEquals(" 新名称 ", request.getName());
        // 归属未变，即使历史品牌已禁用也不重新校验其状态。
        verifyNoInteractions(categories, brands, generator);
    }

    @Test
    void changingBoth_checksTargetCategoryThenBrandAndBinding() {
        request.setCategoryId(4L);
        request.setBrandId(5L);
        category(4L);
        brand(5L, 1);
        when(categories.existsByCategoryIdAndBrandId(4L, 5L)).thenReturn(true);
        service.updateDraft(1L, request);
        var order = inOrder(categories, brands, mapper);
        order.verify(mapper).selectByIdForUpdate(1L);
        order.verify(categories).selectByIdForUpdate(4L);
        order.verify(categories).isLeafCategory(4L);
        order.verify(brands).selectByIdForUpdate(5L);
        order.verify(categories).existsByCategoryIdAndBrandId(4L, 5L);
        order.verify(mapper).updateDraftById(1L, 128, "新名称", 4L, 5L, "main.png", List.of(), List.of(), null, 0L);
    }

    @Test
    void clearingBrand_passesNullToSqlWithoutBrandLookup() {
        request.setBrandId(null);
        category(2L);
        service.updateDraft(1L, request);
        verify(mapper).updateDraftById(1L, 128, "新名称", 2L, null, "main.png", List.of(), List.of(), null, 0L);
        verifyNoInteractions(brands);
    }

    @Test
    void changingOnlyCategory_rechecksExistingBrandBinding() {
        request.setCategoryId(4L);
        category(4L);
        brand(3L, 1);
        var error = assertThrows(BizException.class, () -> service.updateDraft(1L, request));
        assertEquals(ProductErrorCode.CATEGORY_BRAND_NOT_BOUND.getCode(), error.getCode());
        verify(categories).existsByCategoryIdAndBrandId(4L, 3L);
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "notDraft", "version"})
    void invalidSpu_failsBeforeReferences(String scenario) {
        ProductErrorCode expected;
        switch (scenario) {
            case "missing" -> { when(mapper.selectByIdForUpdate(1L)).thenReturn(null); expected = ProductErrorCode.SPU_NOT_FOUND; }
            case "deleted" -> { existing.setDeleted(1L); expected = ProductErrorCode.SPU_NOT_FOUND; }
            case "notDraft" -> { existing.setStatus(1); expected = ProductErrorCode.SPU_NOT_DRAFT; }
            default -> { request.setVersion(127); expected = ProductErrorCode.SPU_VERSION_CONFLICT; }
        }
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.updateDraft(1L, request)).getCode());
        assertNoUpdate();
        verifyNoInteractions(categories, brands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoryMissing", "categoryDisabled", "nonLeaf", "brandMissing", "brandDisabled", "unbound"})
    void changedOwnership_rejectsInvalidReferences(String scenario) {
        request.setBrandId(5L);
        ProductErrorCode expected;
        if (scenario.equals("categoryMissing")) {
            expected = ProductErrorCode.CATEGORY_NOT_FOUND;
        } else {
            category(2L);
            switch (scenario) {
                case "categoryDisabled" -> {
                    ProductCategory disabled = new ProductCategory(); disabled.setStatus(0);
                    when(categories.selectByIdForUpdate(2L)).thenReturn(disabled);
                    expected = ProductErrorCode.CATEGORY_DISABLED;
                }
                case "nonLeaf" -> { when(categories.isLeafCategory(2L)).thenReturn(false); expected = ProductErrorCode.CATEGORY_NOT_LEAF; }
                case "brandMissing" -> expected = ProductErrorCode.BRAND_NOT_FOUND;
                case "brandDisabled" -> { brand(5L, 0); expected = ProductErrorCode.BRAND_DISABLED; }
                default -> { brand(5L, 1); expected = ProductErrorCode.CATEGORY_BRAND_NOT_BOUND; }
            }
        }
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.updateDraft(1L, request)).getCode());
        assertNoUpdate();
    }

    @Test
    void mainImageDuplicateAfterTrim_failsBeforeLocks() {
        request.setBrandId(5L);
        request.setCarouselImages(List.of("main.png"));
        assertEquals(ProductErrorCode.MAIN_IMAGE_IN_CAROUSEL.getCode(),
                assertThrows(BizException.class, () -> service.updateDraft(1L, request)).getCode());
        assertNoUpdate();
        verifyNoInteractions(categories, brands);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unexpectedAffectedRows_isNotSuccess(int rows) {
        when(mapper.updateDraftById(anyLong(), anyInt(), anyString(), anyLong(), nullable(Long.class),
                anyString(), anyList(), anyList(), nullable(String.class), anyLong())).thenReturn(rows);
        var error = assertThrows(BizException.class, () -> service.updateDraft(1L, request));
        assertEquals((rows == 0 ? ProductErrorCode.SPU_VERSION_CONFLICT : ProductErrorCode.SPU_UPDATE_FAILED).getCode(), error.getCode());
    }

    @Test
    void databaseFailure_propagates() {
        var failure = new DataAccessResourceFailureException("测试数据库异常");
        when(mapper.updateDraftById(anyLong(), anyInt(), anyString(), anyLong(), nullable(Long.class),
                anyString(), anyList(), anyList(), nullable(String.class), anyLong())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.updateDraft(1L, request)));
    }

    private SpecDTO spec(String name, String... values) {
        var spec = new SpecDTO();
        spec.setName(name);
        spec.setValues(List.of(values));
        return spec;
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove", "rename", "value", "dimensionOrder", "valueOrder"})
    @org.junit.jupiter.api.DisplayName("已有SKU时禁止增删改规格以及维度和值重排")
    void generatedSkus_freezeSpecs(String change) {
        existing.setSpecs(List.of(spec("颜色", "红", "蓝"), spec("尺寸", "S", "M")));
        request.setSpecs(switch (change) {
            case "add" -> List.of(spec("颜色", "红", "蓝"), spec("尺寸", "S", "M"), spec("材质", "棉"));
            case "remove" -> List.of(spec("颜色", "红", "蓝"));
            case "rename" -> List.of(spec("色彩", "红", "蓝"), spec("尺寸", "S", "M"));
            case "value" -> List.of(spec("颜色", "黑", "蓝"), spec("尺寸", "S", "M"));
            case "dimensionOrder" -> List.of(spec("尺寸", "S", "M"), spec("颜色", "红", "蓝"));
            default -> List.of(spec("颜色", "蓝", "红"), spec("尺寸", "S", "M"));
        });
        when(skus.countActiveBySpuId(1L)).thenReturn(6);
        assertEquals(ProductErrorCode.SPU_SPECS_FROZEN.getCode(),
                assertThrows(BizException.class, () -> service.updateDraft(1L, request)).getCode());
        var order = inOrder(mapper, skus);
        order.verify(mapper).selectByIdForUpdate(1L);
        order.verify(skus).countActiveBySpuId(1L);
        assertNoUpdate();
        verifyNoInteractions(categories, brands);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("规范化后规格不变时可修改名称，不查询SKU且不修改原规格对象")
    void unchangedNormalizedSpecs_allowNonSpecEdit() {
        var old = spec("颜色", "红", "蓝");
        existing.setSpecs(List.of(old));
        var submitted = spec(" 颜色 ", " 红 ", "蓝");
        request.setSpecs(List.of(submitted));
        service.updateDraft(1L, request);
        assertEquals(List.of(spec("颜色", "红", "蓝")), existing.getSpecs());
        assertNotSame(old, existing.getSpecs().getFirst());
        assertNotSame(submitted, existing.getSpecs().getFirst());
        assertEquals(" 颜色 ", submitted.getName());
        verifyNoInteractions(skus);
        verify(mapper).updateDraftById(1L, 128, "新名称", 2L, 3L, "main.png", List.of(),
                List.of(spec("颜色", "红", "蓝")), null, 0L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("尚未生成SKU时允许修改规格")
    void noSkus_allowSpecEdit() {
        request.setSpecs(List.of(spec("颜色", "红")));
        service.updateDraft(1L, request);
        verify(skus).countActiveBySpuId(1L);
        verify(mapper).updateDraftById(1L, 128, "新名称", 2L, 3L, "main.png", List.of(), request.getSpecs(), null, 0L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("默认SKU同样冻结无规格定义")
    void defaultSku_blocksAddingSpec() {
        request.setSpecs(List.of(spec("颜色", "红")));
        when(skus.countActiveBySpuId(1L)).thenReturn(1);
        assertEquals(ProductErrorCode.SPU_SPECS_FROZEN.getCode(),
                assertThrows(BizException.class, () -> service.updateDraft(1L, request)).getCode());
        assertNoUpdate();
    }
}
