package com.flowmart.product.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 解析真实 XML 并绑定 JDBC 参数；不连接 MySQL，不替代真实数据及事务验证。 */
class CategoryBrandUnbindSqlTest {
    private Configuration configuration(String resource) throws Exception {
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test
    @DisplayName("旧绑定查询只读取当前类目有效关系，不按品牌状态过滤")
    void oldBindings_readsRawActiveRelations() throws Exception {
        var config = configuration("mapper/ProductBrandMapper.xml");
        var method = ProductBrandMapper.class.getMethod("selectBoundBrandIdsByCategoryId", Long.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(new Object[]{17L});
        var statement = config.getMappedStatement(ProductBrandMapper.class.getName()
                + ".selectBoundBrandIdsByCategoryId");
        var bound = statement.getBoundSql(params);

        String sql = bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertEquals("select brand_id from product_category_brand where category_id = ? "
                + "and deleted = 0 order by brand_id", sql);
        assertEquals(Long.class, statement.getResultMaps().getFirst().getType());
        assertEquals(1, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setLong(1, 17L);
    }

    @Test
    @DisplayName("SPU引用SQL限定类目与品牌组合、排除已删除商品、不限制商品状态")
    void referenceQuery_scopesCategoryAndBrandsAndBindsForeach() throws Exception {
        var config = configuration("mapper/ProductSpuMapper.xml");
        var method = ProductSpuMapper.class.getMethod("existsByCategoryIdAndBrandIds", Long.class, List.class);
        Object params = new ParamNameResolver(config, method)
                .getNamedParams(new Object[]{17L, List.of(23L, 29L)});
        var statement = config.getMappedStatement(ProductSpuMapper.class.getName()
                + ".existsByCategoryIdAndBrandIds");
        var bound = statement.getBoundSql(params);

        String sql = bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("select exists ( select 1 from product_spu"));
        assertTrue(sql.contains("where category_id = ? and deleted = 0 and brand_id in"));
        assertFalse(sql.contains("status"), "草稿、上架和下架商品都应保护绑定");
        assertEquals(3, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setLong(1, 17L);
        verify(jdbc).setLong(2, 23L);
        verify(jdbc).setLong(3, 29L);
    }
}
