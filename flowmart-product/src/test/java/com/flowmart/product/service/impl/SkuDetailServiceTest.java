package com.flowmart.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.enums.SpuStatus;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实转换器和生成 SQL 的测试；不连接 MySQL，不替代真实数据库验收。 */
class SkuDetailServiceTest {
    private ProductSkuMapper skuMapper;
    private ProductSpuMapper spuMapper;
    private SkuServiceImpl service;

    @BeforeEach
    void setUp() {
        skuMapper = mock(ProductSkuMapper.class);
        spuMapper = mock(ProductSpuMapper.class);
        service = new SkuServiceImpl(Mappers.getMapper(SkuConverter.class), skuMapper, spuMapper);
    }

    @Test
    @DisplayName("SKU查询无结果时返回SKU_NOT_FOUND，不查询所属SPU")
    void missingSku_stopsBeforeSpuQuery() {
        // 不存在和被逻辑删除过滤掉的记录，Mapper 均返回 null；过滤 SQL 另测。
        when(skuMapper.selectById(100L)).thenReturn(null);
        var error = assertThrows(BizException.class, () -> service.detailSku(100L));
        assertEquals(ProductErrorCode.SKU_NOT_FOUND.getCode(), error.getCode());
        verify(skuMapper).selectById(100L);
        verifyNoMoreInteractions(skuMapper);
        verifyNoInteractions(spuMapper);
    }

    @Test
    @DisplayName("SKU存在但所属SPU查询无结果时返回SPU_NOT_FOUND")
    void missingSpu_rejectsOrphanSku() {
        when(skuMapper.selectById(100L)).thenReturn(sku());
        when(spuMapper.selectById(42L)).thenReturn(null);
        var error = assertThrows(BizException.class, () -> service.detailSku(100L));
        assertEquals(ProductErrorCode.SPU_NOT_FOUND.getCode(), error.getCode());
        verifyReadOrder();
    }

    @Test
    @DisplayName("所属SPU已删除时防御性校验拒绝返回SKU详情")
    void deletedSpu_isRejected() {
        var spu = spu(SpuStatus.DRAFT);
        spu.setDeleted(42L);
        when(skuMapper.selectById(100L)).thenReturn(sku());
        when(spuMapper.selectById(42L)).thenReturn(spu);
        var error = assertThrows(BizException.class, () -> service.detailSku(100L));
        assertEquals(ProductErrorCode.SPU_NOT_FOUND.getCode(), error.getCode());
        verifyReadOrder();
    }

    @ParameterizedTest
    @EnumSource(SpuStatus.class)
    @DisplayName("草稿、上架和下架SPU均允许查询SKU详情，按SKU归属查询SPU")
    void allSpuStatuses_areReadable(SpuStatus status) {
        when(skuMapper.selectById(100L)).thenReturn(sku());
        when(spuMapper.selectById(42L)).thenReturn(spu(status));
        var result = service.detailSku(100L);
        assertEquals(100L, result.getId());
        assertEquals(42L, result.getSpuId());
        verifyReadOrder();
    }

    @Test
    @DisplayName("真实转换器完整映射详情与审计字段，规格文本和数组保留存储顺序")
    void detail_mapsAllFieldsAndPreservesSpecOrder() {
        var row = sku();
        when(skuMapper.selectById(100L)).thenReturn(row);
        when(spuMapper.selectById(42L)).thenReturn(spu(SpuStatus.DRAFT));
        var result = service.detailSku(100L);
        assertAll(
                () -> assertEquals(row.getId(), result.getId()),
                () -> assertEquals(row.getSpuId(), result.getSpuId()),
                () -> assertEquals(row.getSkuCode(), result.getSkuCode()),
                () -> assertEquals(row.getSpecValues(), result.getSpecValues()),
                () -> assertEquals("颜色=黑色 / 尺码=M", result.getSpecText()),
                () -> assertEquals(row.getPrice(), result.getPrice()),
                () -> assertEquals(row.getImageUrl(), result.getImageUrl()),
                () -> assertFalse(result.getIsDefault()),
                () -> assertEquals(row.getVersion(), result.getVersion()),
                () -> assertEquals(row.getCreatedBy(), result.getCreatedBy()),
                () -> assertEquals(row.getCreatedAt(), result.getCreatedAt()),
                () -> assertEquals(row.getUpdatedBy(), result.getUpdatedBy()),
                () -> assertEquals(row.getUpdatedAt(), result.getUpdatedAt()));
        assertEquals(List.of("颜色", "尺码"), row.getSpecValues().stream().map(SkuSpecValueDTO::getName).toList());
        verifyReadOrder();
    }

    @Test
    @DisplayName("默认SKU详情保留空规格数组和默认标记，展示默认规格")
    void defaultSku_returnsEmptySpecsAndDefaultText() {
        var row = sku();
        row.setSpecValues(List.of());
        row.setIsDefault(true);
        when(skuMapper.selectById(100L)).thenReturn(row);
        when(spuMapper.selectById(42L)).thenReturn(spu(SpuStatus.DRAFT));
        var result = service.detailSku(100L);
        assertEquals(List.of(), result.getSpecValues());
        assertEquals("默认规格", result.getSpecText());
        assertTrue(result.getIsDefault());
    }

    @Test
    @DisplayName("真实SKU selectById语句绑定SKU ID并排除已删除记录")
    void skuSelectById_filtersLogicalDeletion() throws Exception {
        assertSelectByIdSql(ProductSkuMapper.class, "product_sku", 100L);
    }

    @Test
    @DisplayName("真实SPU selectById语句绑定所属SPU ID并排除已删除记录")
    void spuSelectById_filtersLogicalDeletion() throws Exception {
        assertSelectByIdSql(ProductSpuMapper.class, "product_spu", 42L);
    }

    private void assertSelectByIdSql(Class<?> mapperType, String table, long id) throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(mapperType);
        var statement = configuration.getMappedStatement(mapperType.getName() + ".selectById");
        var params = Map.of("id", id);
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("FROM " + table), sql);
        String where = sql.substring(sql.indexOf("WHERE")).replaceAll("\\s+", "");
        assertEquals("WHEREid=?ANDdeleted=0", where);
        assertEquals(1, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setLong(1, id);
    }

    private void verifyReadOrder() {
        var order = inOrder(skuMapper, spuMapper);
        order.verify(skuMapper).selectById(100L);
        order.verify(spuMapper).selectById(42L);
        verifyNoMoreInteractions(skuMapper, spuMapper);
    }

    private ProductSpu spu(SpuStatus status) {
        var spu = new ProductSpu();
        spu.setId(42L);
        spu.setDeleted(0L);
        spu.setStatus(status.getCode());
        return spu;
    }

    private ProductSku sku() {
        var row = new ProductSku();
        row.setId(100L);
        row.setSpuId(42L);
        row.setSkuCode("SKU100");
        row.setSpecValues(List.of(spec("颜色", "黑色"), spec("尺码", "M")));
        row.setPrice(new BigDecimal("99.90"));
        row.setImageUrl("https://example.test/sku.png");
        row.setIsDefault(false);
        row.setVersion(3);
        row.setDeleted(0L);
        row.setCreatedBy(7L);
        row.setUpdatedBy(8L);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 4, 10, 0));
        row.setUpdatedAt(row.getCreatedAt().plusHours(1));
        return row;
    }

    private SkuSpecValueDTO spec(String name, String value) {
        var spec = new SkuSpecValueDTO();
        spec.setName(name);
        spec.setValue(value);
        return spec;
    }
}
