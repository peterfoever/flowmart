package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.*;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证业务编排；Mockito 不模拟数据库事务和锁。 */
@ExtendWith(MockitoExtension.class)
class CategoryBrandServiceImplTest {
    @Mock ProductBrandMapper brands;
    @Mock ProductCategoryMapper categories;
    @InjectMocks CategoryBrandServiceImpl service;

    private ReplaceCategoryBrandsDTO request(List<Long> ids) {
        var request = new ReplaceCategoryBrandsDTO();
        request.setBrandIds(ids);
        return request;
    }

    private ProductBrand brand(long id, int status) {
        var brand = new ProductBrand();
        brand.setId(id);
        brand.setStatus(status);
        return brand;
    }

    private void leaf() {
        when(categories.selectByIdForUpdate(1L)).thenReturn(new ProductCategory());
        when(categories.isLeafCategory(1L)).thenReturn(true);
    }

    @Test
    void duplicateIds_areDeduplicatedAndAuditFieldsAreFilled() {
        leaf();
        when(brands.selectByIdsAndDeleted(List.of(2L, 3L), 0L))
                .thenReturn(List.of(brand(2, 1), brand(3, 1)));
        when(brands.batchInsert(anyList())).thenAnswer(call -> {
            List<ProductCategoryBrand> rows = call.getArgument(0);
            assertEquals(List.of(2L, 3L), rows.stream().map(ProductCategoryBrand::getBrandId).toList());
            assertEquals(2, rows.stream().map(ProductCategoryBrand::getId).distinct().count());
            for (var row : rows) {
                assertNotNull(row.getId());
                assertEquals(1L, row.getCategoryId());
                assertEquals(0L, row.getDeleted());
                assertEquals(0, row.getVersion());
                assertEquals(0L, row.getCreatedBy());
                assertEquals(0L, row.getUpdatedBy());
                assertNotNull(row.getCreatedAt());
                assertEquals(row.getCreatedAt(), row.getUpdatedAt());
            }
            return rows.size();
        });

        service.replaceCategoryBrands(1L, request(List.of(2L, 2L, 3L)));

        var order = inOrder(categories, brands);
        order.verify(categories).selectByIdForUpdate(1L);
        order.verify(categories).isLeafCategory(1L);
        order.verify(brands).selectByIdsAndDeleted(List.of(2L, 3L), 0L);
        order.verify(brands).logicDeleteByCategoryId(1L, 0L);
        order.verify(brands).batchInsert(anyList());
    }

    @Test
    void emptyList_onlyClearsBindings() {
        leaf();
        service.replaceCategoryBrands(1L, request(List.of()));
        verify(brands).logicDeleteByCategoryId(1L, 0L);
        verifyNoMoreInteractions(brands);
    }

    @Test
    void invalidIds_areRejectedBeforeLocking() {
        for (List<Long> ids : Arrays.asList(null, Arrays.asList((Long) null), List.of(0L), List.of(-1L))) {
            var error = assertThrows(BizException.class, () -> service.replaceCategoryBrands(1L, request(ids)));
            assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), error.getCode());
        }
        assertThrows(BizException.class, () -> service.replaceCategoryBrands(1L, null));
        assertThrows(BizException.class, () -> service.replaceCategoryBrands(0L, request(List.of())));
        verifyNoInteractions(categories, brands);
    }

    @Test
    void missingCategory_doesNotWrite() {
        var error = assertThrows(BizException.class, () -> service.replaceCategoryBrands(1L, request(List.of(2L))));
        assertEquals(ProductErrorCode.CATEGORY_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(brands);
    }

    @Test
    void nonLeafCategory_doesNotWrite() {
        when(categories.selectByIdForUpdate(1L)).thenReturn(new ProductCategory());
        var error = assertThrows(BizException.class, () -> service.replaceCategoryBrands(1L, request(List.of(2L))));
        assertEquals(ProductErrorCode.CATEGORY_NOT_LEAF.getCode(), error.getCode());
        verifyNoInteractions(brands);
    }

    @Test
    void missingBrand_doesNotDeleteOldBindings() {
        leaf();
        when(brands.selectByIdsAndDeleted(List.of(2L, 3L), 0L)).thenReturn(List.of(brand(2, 1)));
        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of(2L, 3L))));
        assertEquals(ProductErrorCode.BRAND_NOT_FOUND.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("3"));
        verify(brands, never()).logicDeleteByCategoryId(anyLong(), anyLong());
        verify(brands, never()).batchInsert(anyList());
    }

    @Test
    void disabledBrand_doesNotDeleteOldBindings() {
        leaf();
        when(brands.selectByIdsAndDeleted(List.of(2L), 0L)).thenReturn(List.of(brand(2, 0)));
        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of(2L))));
        assertEquals(ProductErrorCode.BRAND_DISABLED.getCode(), error.getCode());
        verify(brands, never()).logicDeleteByCategoryId(anyLong(), anyLong());
    }

    @Test
    void insertCountMismatch_throwsToTransactionBoundary() {
        leaf();
        when(brands.selectByIdsAndDeleted(List.of(2L), 0L)).thenReturn(List.of(brand(2, 1)));
        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of(2L))));
        assertEquals(ProductErrorCode.CATEGORY_BRAND_REPLACE_FAILED.getCode(), error.getCode());
        verify(brands).logicDeleteByCategoryId(1L, 0L);
    }

    @Test
    void insertException_isNotSwallowed() {
        leaf();
        when(brands.selectByIdsAndDeleted(List.of(2L), 0L)).thenReturn(List.of(brand(2, 1)));
        var failure = new DataAccessResourceFailureException("模拟插入失败");
        when(brands.batchInsert(anyList())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of(2L)))));
    }

    @Test
    void largeRequest_validatesAllBatchesBeforeWriting() {
        leaf();
        List<Long> ids = LongStream.rangeClosed(1, 501).boxed().toList();
        when(brands.selectByIdsAndDeleted(anyList(), eq(0L))).thenAnswer(call -> {
            List<Long> batch = call.getArgument(0);
            assertTrue(batch.size() <= 500);
            return batch.stream().map(id -> brand(id, 1)).toList();
        });
        when(brands.batchInsert(anyList())).thenAnswer(call -> {
            List<?> batch = call.getArgument(0);
            assertTrue(batch.size() <= 500);
            return batch.size();
        });
        service.replaceCategoryBrands(1L, request(ids));
        var order = inOrder(brands);
        order.verify(brands).selectByIdsAndDeleted(ids.subList(0, 500), 0L);
        order.verify(brands).selectByIdsAndDeleted(List.of(501L), 0L);
        order.verify(brands).logicDeleteByCategoryId(1L, 0L);
        order.verify(brands).batchInsert(argThat(rows -> rows.size() == 500));
        order.verify(brands).batchInsert(argThat(rows -> rows.size() == 1));
    }
}
