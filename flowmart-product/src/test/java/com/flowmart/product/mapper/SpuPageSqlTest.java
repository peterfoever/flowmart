package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.flowmart.product.dto.SpuQueryDTO;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 加载真实 XML，解析生成的 SQL 并验证 JDBC 绑定；不是 MySQL 集成测试。 */
class SpuPageSqlTest {
    private MybatisConfiguration config;

    @BeforeEach
    void loadXml() throws Exception {
        config = new MybatisConfiguration();
        String resource = "mapper/ProductSpuMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("默认SQL语法正确且保留轻量字段左连接和稳定排序")
    void defaultSql_isValidAndLightweight_withStableSortAndLeftJoins() throws Exception {
        var page = query(false, new SpuQueryDTO(), null, null, 0);
        var count = query(true, new SpuQueryDTO(), null, null, 0);
        assertNotNull(CCJSqlParserUtil.parse(page.sql()));
        assertNotNull(CCJSqlParserUtil.parse(count.sql()));
        assertTrue(page.sql().contains("spu.version, c.name AS categoryName"));
        assertTrue(page.sql().contains("LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0"));
        assertTrue(page.sql().contains("LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0"));
        assertTrue(page.sql().endsWith("ORDER BY spu.created_at DESC, spu.id DESC LIMIT ? OFFSET ?"));
        assertEquals("SELECT COUNT(1) FROM product_spu spu WHERE spu.deleted = 0", count.sql());
        assertEquals("WHERE spu.deleted = 0", where(page.sql()));
        for (String heavy : List.of("carousel_images", "spec_json", "description", "product_sku")) {
            assertFalse(page.sql().contains(heavy));
        }
        for (String field : List.of("spu.id", "spu.name", "AS spuCode", "AS categoryId", "AS brandId",
                "AS mainImageUrl", "AS brandName", "spu.status", "AS createdAt", "AS updatedAt")) {
            assertTrue(page.sql().contains(field), field);
        }
        var jdbc = mock(PreparedStatement.class); page.bind(jdbc);
        verify(jdbc).setInt(1, 20); verify(jdbc).setLong(2, 0L); verifyNoMoreInteractions(jdbc);
    }

    @Test
    @DisplayName("全部条件共享WHERE且JDBC按实际方法参数安全绑定")
    void allFilters_shareWhereClause_andBindValuesInOrder() throws Exception {
        var q = new SpuQueryDTO(); q.setPageSize(5); q.setSpuCode("SPU-1");
        q.setBrandId(9L); q.setNoBrand(false); q.setStatus(0);
        q.setCreatedFrom("2026-10-01 00:00:00"); q.setCreatedTo("2026-10-09 00:00:00");
        String pattern = "%a!!!%!_' OR 1=1 --%";
        var page = query(false, q, pattern, List.of(3L, 4L), 10);
        var count = query(true, q, pattern, List.of(3L, 4L), 10);
        assertNotNull(CCJSqlParserUtil.parse(page.sql())); assertNotNull(CCJSqlParserUtil.parse(count.sql()));
        assertEquals(where(count.sql()), where(page.sql()));
        assertFalse(count.sql().contains("JOIN")); assertFalse(page.sql().contains(pattern));
        assertTrue(page.sql().contains("spu.name LIKE ? ESCAPE '!'"));
        assertTrue(page.sql().contains("AND spu.spu_code = ?"));
        assertTrue(page.sql().contains("AND spu.category_id IN"));
        assertTrue(page.sql().contains("AND spu.brand_id = ?"));
        assertTrue(page.sql().contains("AND spu.status = ?"));
        assertTrue(page.sql().contains("AND spu.created_at >= ?"));
        assertTrue(page.sql().contains("AND spu.created_at < ?"));
        assertFalse(page.sql().contains("brand_id IS NULL"));
        for (var built : List.of(page, count)) {
            var jdbc = mock(PreparedStatement.class); built.bind(jdbc);
            verify(jdbc).setString(1, pattern); verify(jdbc).setString(2, "SPU-1");
            verify(jdbc).setLong(3, 3L); verify(jdbc).setLong(4, 4L);
            verify(jdbc).setLong(5, 9L); verify(jdbc).setInt(6, 0);
            verify(jdbc).setString(7, q.getCreatedFrom()); verify(jdbc).setString(8, q.getCreatedTo());
            if (built == page) { verify(jdbc).setInt(9, 5); verify(jdbc).setLong(10, 10L); }
            verifyNoMoreInteractions(jdbc);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("空类目范围生成恒假条件而非省略筛选")
    void emptyCategoryScope_neverBecomesUnfiltered(boolean count) throws Exception {
        var built = query(count, new SpuQueryDTO(), null, List.of(), 0);
        assertTrue(built.sql().contains("AND 1 = 0"));
        assertFalse(built.sql().contains("IN ("));
        assertNotNull(CCJSqlParserUtil.parse(built.sql()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("无品牌筛选使用主表IS NULL")
    void noBrandTrue_addsIsNullWithoutJoinFilter(boolean count) throws Exception {
        var q = new SpuQueryDTO(); q.setNoBrand(true);
        var built = query(count, q, null, null, 0);
        assertEquals("WHERE spu.deleted = 0 AND spu.brand_id IS NULL", where(built.sql()));
        assertNotNull(CCJSqlParserUtil.parse(built.sql()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("单端时间SQL遵守左闭右开")
    void singleTimeBoundary_usesCorrectInclusiveOrExclusiveOperator(boolean fromOnly) throws Exception {
        var q = new SpuQueryDTO();
        if (fromOnly) q.setCreatedFrom("2026-10-01 00:00:00"); else q.setCreatedTo("2026-10-01 00:00:00");
        for (boolean count : List.of(true, false)) {
            String sql = query(count, q, null, null, 0).sql();
            assertEquals("WHERE spu.deleted = 0 AND spu.created_at " + (fromOnly ? ">=" : "<") + " ?", where(sql));
            assertNotNull(CCJSqlParserUtil.parse(sql));
        }
    }

    private Built query(boolean count, SpuQueryDTO q, String keyword, List<Long> ids, long offset) throws Exception {
        String name = count ? "countSpu" : "selectSpuPage";
        var method = count ? ProductSpuMapper.class.getMethod(name, SpuQueryDTO.class, String.class, List.class)
                : ProductSpuMapper.class.getMethod(name, SpuQueryDTO.class, String.class, List.class, long.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(count
                ? new Object[]{q, keyword, ids} : new Object[]{q, keyword, ids, offset});
        var statement = config.getMappedStatement(ProductSpuMapper.class.getName() + "." + name);
        return new Built(statement, params, statement.getBoundSql(params));
    }

    private String where(String sql) {
        String clause = sql.substring(sql.indexOf("WHERE"));
        int order = clause.indexOf(" ORDER BY");
        return order < 0 ? clause : clause.substring(0, order);
    }

    private record Built(MappedStatement statement, Object params, BoundSql bound) {
        String sql() { return bound.getSql().replaceAll("\\s+", " ").trim(); }
        void bind(PreparedStatement jdbc) {
            new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        }
    }
}
