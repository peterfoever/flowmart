package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductBrandMapper;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证差集和调用顺序；不模拟真实 MySQL 行锁、引用查询结果或事务回滚。 */
@ExtendWith(MockitoExtension.class)
class CategoryBrandUnbindProtectionTest {
    @Mock ProductBrandMapper brands;
    @Mock ProductCategoryMapper categories;
    @Mock ProductSpuMapper spus;
    @InjectMocks CategoryBrandServiceImpl service;

    private ReplaceCategoryBrandsDTO request(List<Long> ids) {
        var request = new ReplaceCategoryBrandsDTO();
        request.setBrandIds(ids);
        return request;
    }

    private void oldBindings(List<Long> ids) {
        when(categories.selectByIdForUpdate(1L)).thenReturn(new ProductCategory());
        when(categories.isLeafCategory(1L)).thenReturn(true);
        when(brands.selectBoundBrandIdsByCategoryId(1L)).thenReturn(ids);
    }

    private void validBrands(List<Long> ids) {
        when(brands.selectByIdsForUpdate(ids)).thenReturn(ids.stream().map(id -> {
            var brand = new ProductBrand();
            brand.setId(id);
            brand.setStatus(1);
            return brand;
        }).toList());
    }

    private void insertSucceeds() {
        when(brands.batchInsert(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
    }

    private void noWrites() {
        verify(brands, never()).logicDeleteByCategoryId(anyLong(), anyLong());
        verify(brands, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("待解绑品牌被当前类目商品引用时拒绝，并且不执行任何写入")
    void referencedRemoval_rejectsBeforeWriting() {
        oldBindings(List.of(2L, 3L));
        validBrands(List.of(3L, 4L));
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(2L))).thenReturn(true);

        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of(3L, 4L))));

        assertEquals(ProductErrorCode.CATEGORY_BRAND_IN_USE_BY_SPU.getCode(), error.getCode());
        verify(spus).existsByCategoryIdAndBrandIds(1L, List.of(2L));
        verifyNoMoreInteractions(spus);
        noWrites();
    }

    @Test
    @DisplayName("只检查被移除的品牌，不把保留或新增品牌传给引用检查")
    void replacement_checksOnlyDifferenceBeforeWriting() {
        oldBindings(List.of(2L, 3L));
        validBrands(List.of(3L, 4L));
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(2L))).thenReturn(false);
        insertSucceeds();

        service.replaceCategoryBrands(1L, request(List.of(4L, 3L, 3L)));

        var order = inOrder(categories, brands, spus);
        order.verify(categories).selectByIdForUpdate(1L);
        order.verify(categories).isLeafCategory(1L);
        order.verify(brands).selectByIdsForUpdate(List.of(3L, 4L));
        order.verify(brands).selectBoundBrandIdsByCategoryId(1L);
        order.verify(spus).existsByCategoryIdAndBrandIds(1L, List.of(2L));
        order.verify(brands).logicDeleteByCategoryId(1L, 0L);
        order.verify(brands).batchInsert(argThat(rows -> rows.size() == 2
                && rows.get(0).getBrandId().equals(3L) && rows.get(1).getBrandId().equals(4L)));
        verifyNoMoreInteractions(spus);
    }

    @Test
    @DisplayName("空数组表示清空，任一旧绑定被引用时禁止清空")
    void clearReferencedBindings_rejects() {
        oldBindings(List.of(2L, 3L));
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(2L, 3L))).thenReturn(true);

        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of())));

        assertEquals(ProductErrorCode.CATEGORY_BRAND_IN_USE_BY_SPU.getCode(), error.getCode());
        verify(spus).existsByCategoryIdAndBrandIds(1L, List.of(2L, 3L));
        verify(brands, never()).selectByIdsForUpdate(anyList());
        noWrites();
    }

    @Test
    @DisplayName("无引用时可以清空全部旧绑定，且不执行插入")
    void clearUnreferencedBindings_deletesAfterChecking() {
        oldBindings(List.of(2L, 3L));
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(2L, 3L))).thenReturn(false);

        service.replaceCategoryBrands(1L, request(List.of()));

        var order = inOrder(brands, spus);
        order.verify(brands).selectBoundBrandIdsByCategoryId(1L);
        order.verify(spus).existsByCategoryIdAndBrandIds(1L, List.of(2L, 3L));
        order.verify(brands).logicDeleteByCategoryId(1L, 0L);
        verify(brands, never()).selectByIdsForUpdate(anyList());
        verify(brands, never()).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("首次绑定、只新增或保留原绑定时跳过空差集查询")
    void noRemovals_skipsReferenceQuery(int oldCount) {
        oldBindings(List.of(2L, 3L).subList(0, oldCount));
        validBrands(List.of(2L, 3L));
        insertSucceeds();

        service.replaceCategoryBrands(1L, request(List.of(3L, 2L, 2L)));

        verifyNoInteractions(spus);
        verify(brands).logicDeleteByCategoryId(1L, 0L);
        verify(brands).batchInsert(anyList());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 501, 1001})
    @DisplayName("待解绑集合每批不超过500条，所有批次检查通过后才删除")
    void largeRemoval_checksEveryBatchBeforeWriting(int count) {
        List<Long> ids = LongStream.rangeClosed(1, count).boxed().toList();
        oldBindings(ids);

        service.replaceCategoryBrands(1L, request(List.of()));

        var order = inOrder(brands, spus);
        order.verify(brands).selectBoundBrandIdsByCategoryId(1L);
        for (int start = 0; start < count; start += 500) {
            order.verify(spus).existsByCategoryIdAndBrandIds(1L,
                    ids.subList(start, Math.min(start + 500, count)));
        }
        order.verify(brands).logicDeleteByCategoryId(1L, 0L);
        verifyNoMoreInteractions(spus);
        verify(brands, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("第二批发现引用时，第一批也未产生删除或插入")
    void laterBatchReferenced_doesNotPartiallyWrite() {
        List<Long> ids = LongStream.rangeClosed(1, 501).boxed().toList();
        oldBindings(ids);
        when(spus.existsByCategoryIdAndBrandIds(1L, ids.subList(0, 500))).thenReturn(false);
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(501L))).thenReturn(true);

        var error = assertThrows(BizException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of())));

        assertEquals(ProductErrorCode.CATEGORY_BRAND_IN_USE_BY_SPU.getCode(), error.getCode());
        var order = inOrder(spus);
        order.verify(spus).existsByCategoryIdAndBrandIds(1L, ids.subList(0, 500));
        order.verify(spus).existsByCategoryIdAndBrandIds(1L, List.of(501L));
        noWrites();
    }

    @Test
    @DisplayName("引用查询异常向外抛出，不能继续执行绑定修改")
    void referenceQueryFailure_isNotSwallowed() {
        oldBindings(List.of(2L));
        var failure = new DataAccessResourceFailureException("模拟引用查询失败");
        when(spus.existsByCategoryIdAndBrandIds(1L, List.of(2L))).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.replaceCategoryBrands(1L, request(List.of()))));
        noWrites();
    }
}
