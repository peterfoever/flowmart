package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flowmart.product.dto.SkuSpecValueDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 解析实际 Mapper/XML 并验证 JDBC 绑定与 JSON 处理器；不连接 MySQL。 */
class SpuPublishSqlTest {
    private MybatisConfiguration config(String resource) throws Exception {
        var config = new MybatisConfiguration();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    @DisplayName("上架SQL以数字绑定新旧状态，用旧版本匹配，只修改状态版本及审计字段")
    void updateStatus_bindsNumericStatesAndOldVersion(int oldState) throws Exception {
        var config = config("mapper/ProductSpuMapper.xml");
        var method = ProductSpuMapper.class.getMethod("updateStatus", Long.class, Integer.class,
                Integer.class, Integer.class, Long.class, LocalDateTime.class);
        var now = LocalDateTime.of(2026, 10, 17, 12, 0);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(
                new Object[]{42L, oldState, 1, 7, 9L, now});
        var statement = config.getMappedStatement(ProductSpuMapper.class.getName() + ".updateStatus");
        var bound = statement.getBoundSql(parameters);
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, parameters, bound).setParameters(jdbc);
        verify(jdbc).setInt(1, 1);
        verify(jdbc).setLong(2, 9L);
        verify(jdbc).setObject(3, now);
        verify(jdbc).setLong(4, 42L);
        verify(jdbc).setInt(5, oldState);
        verify(jdbc).setInt(6, 7);
        assertEquals("UPDATE product_spu SET status = ?, version = version + 1, updated_by = ?, updated_at = ? "
                        + "WHERE id = ? AND deleted = 0 AND status = ? AND version = ?",
                bound.getSql().replaceAll("\\s+", " ").trim());
    }

    @Test
    @DisplayName("上架SKU查询过滤所属SPU及逻辑删除，有界读取且使用生成的实体ResultMap")
    void skuQuery_usesExplicitJsonResultMap() throws Exception {
        var config = config("mapper/ProductSkuMapper.xml");
        var statement = config.getMappedStatement(ProductSkuMapper.class.getName() + ".selectActiveBySpuId");
        String sql = statement.getBoundSql(Map.of("spuId", 42L)).getSql().replaceAll("\\s+", " ").trim();
        assertFalse(sql.contains("SELECT *"));
        assertTrue(sql.contains("WHERE spu_id = ? AND deleted = 0 ORDER BY id ASC LIMIT 1001"));
        var resultMap = statement.getResultMaps().getFirst();
        assertEquals(ProductSkuMapper.class.getName() + ".mybatis-plus_ProductSku", resultMap.getId());
        var mapping = resultMap.getResultMappings().stream()
                .filter(m -> m.getProperty().equals("specValues")).findFirst().orElseThrow();
        assertEquals("spec_values", mapping.getColumn());
        assertInstanceOf(JacksonTypeHandler.class, mapping.getTypeHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"name\":\"颜色\",\"value\":\"红\"}]"})
    @DisplayName("上架查询所用处理器可还原空数组与有类型的规格DTO")
    @SuppressWarnings("unchecked")
    void queryJsonHandler_restoresTypedList(String json) throws Exception {
        var config = config("mapper/ProductSkuMapper.xml");
        var statement = config.getMappedStatement(ProductSkuMapper.class.getName() + ".selectActiveBySpuId");
        var mapping = statement.getResultMaps().getFirst().getResultMappings().stream()
                .filter(m -> m.getProperty().equals("specValues")).findFirst().orElseThrow();
        var handler = (TypeHandler<List<SkuSpecValueDTO>>) mapping.getTypeHandler();
        var jdbc = mock(ResultSet.class);
        when(jdbc.getString("spec_values")).thenReturn(json);
        List<SkuSpecValueDTO> values = handler.getResult(jdbc, "spec_values");
        assertNotNull(values);
        if (json.equals("[]")) assertTrue(values.isEmpty());
        else {
            assertEquals(1, values.size());
            assertInstanceOf(SkuSpecValueDTO.class, ((List<?>) values).getFirst());
            assertEquals("颜色", values.getFirst().getName());
            assertEquals("红", values.getFirst().getValue());
        }
    }
}
