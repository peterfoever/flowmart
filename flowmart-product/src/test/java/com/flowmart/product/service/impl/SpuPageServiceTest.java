package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.SpuQueryDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.*;
import com.flowmart.product.vo.SpuListVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FM-005 业务回归：真实 Service + Mock Mapper，不依赖数据库。 */
class SpuPageServiceTest {
    private final ProductSpuMapper spus = mock(ProductSpuMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final ProductBrandMapper brands = mock(ProductBrandMapper.class);
    private final SpuServiceImpl service = new SpuServiceImpl(mock(SpuConverter.class), spus,
            categories, brands, mock(SpuCodeGenerator.class), mock(ProductSkuMapper.class));

    @Test
    @DisplayName("默认查询不传时间，保留分页默认值并跳过空列表查询")
    void defaults_withoutTimes_returnEmptyPageAndSkipList() {
        var result = service.page(new SpuQueryDTO());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        var query = ArgumentCaptor.forClass(SpuQueryDTO.class);
        verify(spus).countSpu(query.capture(), isNull(), isNull());
        assertNull(query.getValue().getCreatedFrom());
        assertNull(query.getValue().getCreatedTo());
        verifyNoMoreInteractions(spus);
        verifyNoInteractions(categories, brands);
    }

    @Test
    @DisplayName("COUNT和列表使用同一归一化副本且不污染请求")
    void normalizedCopy_usedByBothQueries_preservesOriginalAndAllFields() {
        var request = new SpuQueryDTO();
        request.setPageNum(3); request.setPageSize(5); request.setNoBrand(true);
        request.setStatus(0); request.setKeyword("  a!%_  "); request.setSpuCode("  SPU-1  ");
        request.setCreatedFrom(" 2026-10-01 00:00:00 ");
        request.setCreatedTo("2026-10-09 00:00:00");
        when(spus.countSpu(any(), any(), isNull())).thenReturn(12L);
        when(spus.selectSpuPage(any(), any(), isNull(), eq(10L))).thenReturn(List.of());

        var result = service.page(request);
        var query = ArgumentCaptor.forClass(SpuQueryDTO.class);
        verify(spus).countSpu(query.capture(), eq("%a!!!%!_%"), isNull());
        var q = query.getValue();
        assertNotSame(request, q);
        assertEquals(3, q.getPageNum()); assertEquals(5, q.getPageSize());
        assertEquals(true, q.getNoBrand()); assertEquals(0, q.getStatus());
        assertEquals("a!%_", q.getKeyword()); assertEquals("SPU-1", q.getSpuCode());
        assertEquals("2026-10-01 00:00:00", q.getCreatedFrom());
        assertEquals("2026-10-09 00:00:00", q.getCreatedTo());
        verify(spus).selectSpuPage(same(q), eq("%a!!!%!_%"), isNull(), eq(10L));
        verifyNoMoreInteractions(spus);
        assertEquals("  a!%_  ", request.getKeyword());
        assertEquals("  SPU-1  ", request.getSpuCode());
        assertEquals(" 2026-10-01 00:00:00 ", request.getCreatedFrom());
        // COUNT 后并发删除导致列表为空，仍保留 COUNT 的结果。
        assertEquals(12, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\u3000"})
    @DisplayName("空白关键词和编码不启用筛选")
    void blankText_doesNotFilter(String text) {
        var request = new SpuQueryDTO(); request.setKeyword(text); request.setSpuCode(text);
        service.page(request);
        var query = ArgumentCaptor.forClass(SpuQueryDTO.class);
        verify(spus).countSpu(query.capture(), isNull(), isNull());
        assertNull(query.getValue().getKeyword()); assertNull(query.getValue().getSpuCode());
        assertEquals(text, request.getSpuCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("单端时间和合法闰日可以查询")
    void oneSidedTime_andLeapDay_areAllowed(boolean fromOnly) {
        var request = new SpuQueryDTO();
        String time = "2024-02-29 23:59:59";
        if (fromOnly) request.setCreatedFrom(time); else request.setCreatedTo(time);
        service.page(request);
        var query = ArgumentCaptor.forClass(SpuQueryDTO.class);
        verify(spus).countSpu(query.capture(), isNull(), isNull());
        assertEquals(fromOnly ? time : null, query.getValue().getCreatedFrom());
        assertEquals(fromOnly ? null : time, query.getValue().getCreatedTo());
    }

    static Stream<Consumer<SpuQueryDTO>> invalidRequests() {
        return Stream.of(q -> q.setPageNum(null), q -> q.setPageNum(0), q -> q.setPageNum(-1),
                q -> q.setPageSize(null), q -> q.setPageSize(0), q -> q.setPageSize(-1),
                q -> q.setPageSize(201), q -> q.setStatus(-1), q -> q.setStatus(3),
                q -> q.setCategoryId(0L), q -> q.setCategoryId(-1L),
                q -> q.setBrandId(0L), q -> q.setBrandId(-1L),
                q -> q.setKeyword("x".repeat(129)), q -> q.setSpuCode("x".repeat(65)),
                q -> { q.setBrandId(1L); q.setNoBrand(true); },
                q -> q.setCreatedFrom("2025-02-29 00:00:00"),
                q -> q.setCreatedTo("2026-02-30 00:00:00"),
                q -> q.setCreatedFrom("2026-10-01T00:00:00"),
                q -> q.setCreatedTo("2026-10-01 24:00:00"),
                q -> { q.setCreatedFrom("2026-10-01 00:00:00"); q.setCreatedTo(q.getCreatedFrom()); },
                q -> { q.setCreatedFrom("2026-10-02 00:00:00"); q.setCreatedTo("2026-10-01 00:00:00"); });
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    @DisplayName("非法参数在任何数据库查询前失败")
    void invalidParameters_failBeforeAnyQuery(Consumer<SpuQueryDTO> change) {
        var request = new SpuQueryDTO(); change.accept(request);
        assertInvalid(request);
        verifyNoInteractions(spus, categories, brands);
    }

    @Test
    @DisplayName("直接调用传null返回参数错误而非空指针")
    void nullRequest_isBusinessError() {
        assertInvalid(null);
        verifyNoInteractions(spus, categories, brands);
    }

    @ParameterizedTest
    @CsvSource({"2,20,20", "3,20,21", "2147483647,200,2147483648"})
    @DisplayName("超页和极大页码保留总数并跳过列表查询")
    void outOfRangeAndLargeOffset_skipList_keepTotal(int page, int size, long total) {
        var request = new SpuQueryDTO(); request.setPageNum(page); request.setPageSize(size);
        when(spus.countSpu(any(), isNull(), isNull())).thenReturn(total);
        var result = service.page(request);
        assertEquals(total, result.getTotal()); assertTrue(result.getRecords().isEmpty());
        assertEquals(page, result.getPageNum()); assertEquals(size, result.getPageSize());
        verify(spus).countSpu(any(), isNull(), isNull()); verifyNoMoreInteractions(spus);
    }

    @Test
    @DisplayName("有效大偏移使用long计算不会溢出")
    void largeOffset_usesLongWithoutOverflow() {
        var request = new SpuQueryDTO(); request.setPageNum(Integer.MAX_VALUE); request.setPageSize(200);
        when(spus.countSpu(any(), isNull(), isNull())).thenReturn(Long.MAX_VALUE);
        service.page(request);
        verify(spus).selectSpuPage(any(), isNull(), isNull(), eq(429496729200L));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("不存在或已删除品牌返回参数错误")
    void missingOrDeletedBrand_isParamInvalid(boolean deleted) {
        var request = new SpuQueryDTO(); request.setBrandId(2L);
        if (deleted) {
            var brand = new ProductBrand(); brand.setDeleted(2L);
            when(brands.selectById(2L)).thenReturn(brand);
        }
        assertInvalid(request); verifyNoInteractions(spus, categories);
    }

    @Test
    @DisplayName("禁用品牌允许筛选且noBrand=false不冲突")
    void disabledBrand_isAllowed_andFalseNoBrandDoesNotConflict() {
        var request = new SpuQueryDTO(); request.setBrandId(2L); request.setNoBrand(false);
        var brand = new ProductBrand(); brand.setDeleted(0L); brand.setStatus(0);
        when(brands.selectById(2L)).thenReturn(brand);
        service.page(request);
        verify(brands).selectById(2L); verifyNoMoreInteractions(brands);
        verify(spus).countSpu(argThat(q -> q.getBrandId() == 2L && !q.getNoBrand()), isNull(), isNull());
        verifyNoInteractions(categories);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("先验证类目根节点不存在或已删除再拒绝请求")
    void missingOrDeletedCategory_isParamInvalid_withoutSubtreeQuery(boolean deleted) {
        var request = new SpuQueryDTO(); request.setCategoryId(3L);
        if (deleted) when(categories.selectById(3L)).thenReturn(category(3L, 3L));
        assertInvalid(request);
        verify(categories).selectById(3L); verifyNoMoreInteractions(categories);
        verifyNoInteractions(spus);
    }

    @Test
    @DisplayName("类目范围包含根及后代且允许禁用节点")
    void categoryIncludesRootAndDescendants_evenDisabled() {
        var request = new SpuQueryDTO(); request.setCategoryId(3L);
        var root = category(3L, 0L); var child = category(4L, 0L);
        when(categories.selectById(3L)).thenReturn(root);
        when(categories.selectSubtree(3L)).thenReturn(List.of(root, child));
        when(spus.countSpu(any(), isNull(), eq(List.of(3L, 4L)))).thenReturn(1L);
        service.page(request);
        var order = inOrder(categories, spus);
        order.verify(categories).selectById(3L); order.verify(categories).selectSubtree(3L);
        order.verify(spus).countSpu(argThat(q -> q.getCategoryId() == 3L), isNull(), eq(List.of(3L, 4L)));
        order.verify(spus).selectSpuPage(any(), isNull(), eq(List.of(3L, 4L)), eq(0L));
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("根校验后子树为空不得扩大为全量查询")
    void emptySubtreeAfterRootCheck_returnsEmpty_withoutBroadeningScope() {
        var request = new SpuQueryDTO(); request.setCategoryId(3L);
        when(categories.selectById(3L)).thenReturn(category(3L, 0L));
        when(categories.selectSubtree(3L)).thenReturn(List.of());
        var result = service.page(request);
        assertEquals(0, result.getTotal()); assertTrue(result.getRecords().isEmpty());
        verifyNoInteractions(spus);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20})
    @DisplayName("展示名称和状态文本不产生逐条查询")
    void displayText_filledWithoutNPlusOne(int count) {
        var records = IntStream.range(0, count).mapToObj(i -> {
            var vo = new SpuListVO(); vo.setId((long) i); vo.setStatus(i % 3);
            vo.setCategoryId(3L); vo.setCategoryName("手机");
            vo.setBrandId(2L); vo.setBrandName("品牌A"); return vo;
        }).toList();
        when(spus.countSpu(any(), isNull(), isNull())).thenReturn((long) count);
        when(spus.selectSpuPage(any(), isNull(), isNull(), eq(0L))).thenReturn(records);
        var result = service.page(new SpuQueryDTO());
        assertEquals(count, result.getRecords().size());
        for (var vo : result.getRecords()) {
            assertEquals("手机", vo.getCategoryText()); assertEquals("品牌A", vo.getBrandText());
            assertEquals(List.of("草稿", "上架", "下架").get(vo.getStatus()), vo.getStatusText());
        }
        verify(spus).countSpu(any(), isNull(), isNull());
        verify(spus).selectSpuPage(any(), isNull(), isNull(), eq(0L));
        verifyNoMoreInteractions(spus); verifyNoInteractions(categories, brands);
    }

    @Test
    @DisplayName("关联失效不移除商品并区分无品牌与品牌失效")
    void missingAssociations_keepRecordsAndDistinguishNoBrand() {
        var noBrand = new SpuListVO(); noBrand.setCategoryId(3L); noBrand.setStatus(0);
        var invalidBrand = new SpuListVO(); invalidBrand.setBrandId(2L); invalidBrand.setStatus(1);
        when(spus.countSpu(any(), isNull(), isNull())).thenReturn(2L);
        when(spus.selectSpuPage(any(), isNull(), isNull(), eq(0L))).thenReturn(List.of(noBrand, invalidBrand));
        var result = service.page(new SpuQueryDTO());
        assertEquals(2, result.getRecords().size());
        assertEquals("无品牌", noBrand.getBrandText()); assertEquals("类目已失效", noBrand.getCategoryText());
        assertNull(noBrand.getCategoryName()); assertEquals(3L, noBrand.getCategoryId());
        assertEquals("品牌已失效", invalidBrand.getBrandText()); assertNull(invalidBrand.getBrandName());
    }

    private void assertInvalid(SpuQueryDTO request) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.page(request)).getCode());
    }

    private ProductCategory category(long id, long deleted) {
        var category = new ProductCategory(); category.setId(id); category.setDeleted(deleted);
        category.setStatus(0); return category;
    }
}
