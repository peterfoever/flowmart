package com.flowmart.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.vo.SkuListVO;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用真实 MapStruct 转换器；SQL 检查不连接数据库，不替代 MySQL 验收。 */
class SkuListServiceTest {
    private ProductSkuMapper skuMapper;
    private ProductSpuMapper spuMapper;
    private SkuServiceImpl service;
    private MybatisConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(ProductSkuMapper.class);
        skuMapper = mock(ProductSkuMapper.class);
        spuMapper = mock(ProductSpuMapper.class);
        service = new SkuServiceImpl(Mappers.getMapper(SkuConverter.class), skuMapper, spuMapper);
    }

    @Test
    @DisplayName("SPU不存在时返回业务错误且不查询SKU")
    void missingSpu_doesNotQuerySku() {
        var error = assertThrows(BizException.class, () -> service.listSku(42L));
        assertEquals(ProductErrorCode.SPU_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(skuMapper);
    }

    @Test
    @DisplayName("逻辑删除的SPU不允许查询SKU")
    void deletedSpu_doesNotQuerySku() {
        var spu = spu(0);
        spu.setDeleted(42L);
        when(spuMapper.selectById(42L)).thenReturn(spu);
        var error = assertThrows(BizException.class, () -> service.listSku(42L));
        assertEquals(ProductErrorCode.SPU_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(skuMapper);
    }

    @Test
    @DisplayName("SPU存在但没有SKU时返回空集合且不额外COUNT")
    void noSkus_returnsEmptyListWithoutCount() {
        when(spuMapper.selectById(42L)).thenReturn(spu(0));
        when(skuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertEquals(List.of(), service.listSku(42L));
        verify(skuMapper).selectList(any(Wrapper.class));
        verifyNoMoreInteractions(skuMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("草稿、上架、下架SPU均可查询有效SKU且保留结果顺序")
    void allSpuStatuses_areReadable(int status) {
        when(spuMapper.selectById(42L)).thenReturn(spu(status));
        when(skuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sku(100L), sku(101L)));
        assertEquals(List.of(100L, 101L), service.listSku(42L).stream().map(v -> v.getId()).toList());
        verify(skuMapper).selectList(any(Wrapper.class));
        verifyNoMoreInteractions(skuMapper);
    }

    @Test
    @DisplayName("真实转换器映射编码、规格、金额、图片、默认标记、版本和时间")
    void listSku_mapsAllPersistedFields() {
        var row = sku(100L);
        var result = query(row);
        assertAll(
                () -> assertEquals(row.getId(), result.getId()),
                () -> assertEquals(row.getSpuId(), result.getSpuId()),
                () -> assertEquals(row.getSkuCode(), result.getSkuCode()),
                () -> assertEquals(row.getSpecValues(), result.getSpecValues()),
                () -> assertEquals(row.getPrice(), result.getPrice()),
                () -> assertEquals(row.getImageUrl(), result.getImageUrl()),
                () -> assertEquals(row.getIsDefault(), result.getIsDefault()),
                () -> assertEquals(row.getVersion(), result.getVersion()),
                () -> assertEquals(row.getCreatedAt(), result.getCreatedAt()),
                () -> assertEquals(row.getUpdatedAt(), result.getUpdatedAt()));
    }

    @Test
    @DisplayName("规格展示文本保留存储顺序，不按规格名重新排序")
    void specText_preservesStoredOrder() {
        var row = sku(100L);
        var values = List.of(spec("颜色", "黑色"), spec("尺码", "M"));
        row.setSpecValues(values);
        row.setIsDefault(false);
        assertEquals("颜色=黑色 / 尺码=M", query(row).getSpecText());
        assertEquals(List.of("颜色", "尺码"), row.getSpecValues().stream().map(SkuSpecValueDTO::getName).toList());
    }

    @Test
    @DisplayName("无规格SKU保留空数组并展示默认规格")
    void defaultSku_hasDefaultSpecText() {
        var result = query(sku(100L));
        assertEquals(List.of(), result.getSpecValues());
        assertTrue(result.getIsDefault());
        assertEquals("默认规格", result.getSpecText());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("Service实际查询生成SQL：按SPU过滤、排除逻辑删除、ID升序，绑定正确ID")
    void actualQuery_filtersAndOrdersCorrectly() throws Exception {
        when(spuMapper.selectById(42L)).thenReturn(spu(0));
        when(skuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.listSku(42L);
        ArgumentCaptor<Wrapper<ProductSku>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(skuMapper).selectList(captor.capture());
        var statement = configuration.getMappedStatement(ProductSkuMapper.class.getName() + ".selectList");
        var params = Map.of(Constants.WRAPPER, captor.getValue());
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("deleted=0"), sql);
        assertTrue(sql.contains("spu_id = ?"), sql);
        assertTrue(sql.endsWith("ORDER BY id ASC"), sql);
        assertEquals(1, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setLong(1, 42L);
        verifyNoMoreInteractions(skuMapper);
    }

    private SkuListVO query(ProductSku row) {
        when(spuMapper.selectById(42L)).thenReturn(spu(0));
        when(skuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        var result = service.listSku(42L);
        assertEquals(1, result.size());
        return result.getFirst();
    }

    private ProductSpu spu(int status) {
        var spu = new ProductSpu();
        spu.setId(42L);
        spu.setDeleted(0L);
        spu.setStatus(status);
        return spu;
    }

    private ProductSku sku(long id) {
        var row = new ProductSku();
        row.setId(id);
        row.setSpuId(42L);
        row.setSkuCode("SKU" + id);
        row.setSpecValues(List.of());
        row.setPrice(new BigDecimal("99.90"));
        row.setImageUrl("https://example.test/sku.png");
        row.setIsDefault(true);
        row.setVersion(3);
        row.setDeleted(0L);
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
