package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.SpecDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 校验真实 XML 的写入参数；不连接 MySQL、不证明实际版本递增或事务回滚。 */
class SpuDraftUpdateSqlTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void update_bindsNullAndJsonAndPreservesProtectedColumns(boolean populated) throws Exception {
        var config = new MybatisConfiguration();
        String resource = "mapper/ProductSpuMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        SpecDTO spec = new SpecDTO();
        spec.setName("颜色"); spec.setValues(List.of("黑色", "白色"));
        List<String> images = populated ? List.of("a.png", "b.png") : List.of();
        List<SpecDTO> specs = populated ? List.of(spec) : List.of();
        var method = ProductSpuMapper.class.getMethod("updateDraftById", Long.class, Integer.class,
                String.class, Long.class, Long.class, String.class, List.class, List.class, String.class, Long.class);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(
                new Object[]{1L, 128, "手机", 2L, null, "main.png", images, specs, null, 42L});
        var statement = config.getMappedStatement(ProductSpuMapper.class.getName() + ".updateDraftById");
        var bound = statement.getBoundSql(parameters);
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, parameters, bound).setParameters(jdbc);
        verify(jdbc).setNull(3, Types.BIGINT);
        verify(jdbc).setNull(7, Types.LONGVARCHAR);
        verify(jdbc).setLong(8, 42L);
        verify(jdbc).setLong(9, 1L);
        verify(jdbc).setInt(10, 128);
        var imageJson = ArgumentCaptor.forClass(String.class);
        var specJson = ArgumentCaptor.forClass(String.class);
        verify(jdbc).setString(eq(5), imageJson.capture());
        verify(jdbc).setString(eq(6), specJson.capture());
        var json = new ObjectMapper();
        assertEquals(json.valueToTree(images), json.readTree(imageJson.getValue()));
        assertEquals(json.valueToTree(specs), json.readTree(specJson.getValue()));
        assertTrue(json.readTree(specJson.getValue()).isArray());
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        String setClause = sql.substring(sql.indexOf("SET"), sql.indexOf("WHERE"));
        for (String column : List.of("spu_code", "status", "created_by", "created_at")) {
            assertFalse(setClause.contains(column));
        }
        assertTrue(setClause.contains("version = version + 1"));
        assertTrue(sql.contains("WHERE id = ? AND deleted = 0 AND status = 0 AND version = ?"));
    }
}
