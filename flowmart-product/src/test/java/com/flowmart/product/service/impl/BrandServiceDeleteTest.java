package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceDeleteTest {
    @Mock ProductBrandMapper mapper;
    @InjectMocks BrandServiceImpl service;

    private ProductBrand brand(int status) {
        var brand = new ProductBrand();
        brand.setId(1L);
        brand.setDeleted(0L);
        brand.setVersion(7);
        brand.setStatus(status);
        return brand;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void unboundBrand_canBeDeletedRegardlessOfStatus(int status) {
        when(mapper.selectByIdForUpdate(1L)).thenReturn(brand(status));
        when(mapper.logicDeleteById(1L, 7, 0L)).thenReturn(1);
        service.deleteBrand(1L);
        var order = inOrder(mapper);
        order.verify(mapper).selectByIdForUpdate(1L);
        order.verify(mapper).existCategoryBindings(1L);
        order.verify(mapper).logicDeleteById(1L, 7, 0L);
    }

    @Test
    void missingBrand_doesNotWrite() {
        var error = assertThrows(BizException.class, () -> service.deleteBrand(1L));
        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(), error.getCode());
        verify(mapper).selectByIdForUpdate(1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void deletedBrand_isRejected() {
        var brand = brand(1);
        brand.setDeleted(1L);
        when(mapper.selectByIdForUpdate(1L)).thenReturn(brand);
        var error = assertThrows(BizException.class, () -> service.deleteBrand(1L));
        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(), error.getCode());
        verify(mapper, never()).logicDeleteById(anyLong(), anyInt(), anyLong());
    }

    @Test
    void activeBinding_preventsDeletion() {
        when(mapper.selectByIdForUpdate(1L)).thenReturn(brand(1));
        when(mapper.existCategoryBindings(1L)).thenReturn(true);
        var error = assertThrows(BizException.class, () -> service.deleteBrand(1L));
        assertEquals(ProductErrorCode.BRAND_BOUND_BY_CATEGORY.getCode(), error.getCode());
        verify(mapper, never()).logicDeleteById(anyLong(), anyInt(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unexpectedRowCount_throws(int count) {
        when(mapper.selectByIdForUpdate(1L)).thenReturn(brand(1));
        when(mapper.logicDeleteById(1L, 7, 0L)).thenReturn(count);
        var error = assertThrows(BizException.class, () -> service.deleteBrand(1L));
        assertEquals(ProductErrorCode.BRAND_DELETE_FAILED.getCode(), error.getCode());
    }

    @Test
    void databaseException_propagatesToTransactionBoundary() {
        when(mapper.selectByIdForUpdate(1L)).thenReturn(brand(1));
        var failure = new DataAccessResourceFailureException("模拟数据库异常");
        when(mapper.logicDeleteById(1L, 7, 0L)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.deleteBrand(1L)));
    }
}
