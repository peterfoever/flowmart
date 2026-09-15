package com.flowmart.product.mapper;

import com.flowmart.product.dto.BrandQueryDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 校验真实 XML 和动态参数绑定，不替代 MySQL 集成测试。 */
class ProductBrandMapperSqlTest {
    @Test
    void deleteSql_usesActualMapperParameterNames() throws Exception {
        var config = configuration();
        var method = ProductBrandMapper.class.getMethod("logicDeleteById", Long.class, Integer.class, Long.class);
        Object params = new org.apache.ibatis.reflection.ParamNameResolver(config, method)
                .getNamedParams(new Object[]{1L, 7, 42L});
        var statement = config.getMappedStatement(ProductBrandMapper.class.getName() + ".logicDeleteById");
        var bound = statement.getBoundSql(params);
        var jdbc = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        org.mockito.Mockito.verify(jdbc).setLong(1, 42L);
        org.mockito.Mockito.verify(jdbc).setLong(2, 1L);
        org.mockito.Mockito.verify(jdbc).setInt(3, 7);
        assertTrue(bound.getSql().contains("deleted = id"));
        assertTrue(bound.getSql().contains("version = version + 1"));
    }

    @Test
    void brandLocksAndBindingFilter_arePresentInRealXml() throws Exception {
        var config = configuration();
        String prefix = ProductBrandMapper.class.getName() + ".";
        var batch = config.getMappedStatement(prefix + "selectByIdsForUpdate")
                .getBoundSql(Map.of("ids", java.util.List.of(2L, 3L)));
        assertTrue(batch.getSql().contains("ORDER BY id ASC"));
        assertTrue(batch.getSql().contains("FOR UPDATE"));
        assertTrue(batch.getSql().contains("deleted = 0"));
        assertEquals(2, batch.getParameterMappings().size());
        var single = config.getMappedStatement(prefix + "selectByIdForUpdate").getBoundSql(Map.of("id", 2L));
        assertTrue(single.getSql().contains("FOR UPDATE"));
        var bindings = config.getMappedStatement(prefix + "existCategoryBindings")
                .getBoundSql(Map.of("brandId", 2L));
        assertTrue(bindings.getSql().contains("deleted = 0"));
    }

    @Test
    void bindingSql_mapsEntityFieldsAndUpdatesVersion() throws Exception {
        Configuration configuration = configuration();
        var binding = new com.flowmart.product.entity.ProductCategoryBrand();
        binding.setId(100L);
        binding.setCategoryId(1L);
        binding.setBrandId(2L);
        binding.setCreatedBy(0L);
        binding.setUpdatedBy(0L);
        binding.setCreatedAt(java.time.LocalDateTime.now());
        binding.setUpdatedAt(binding.getCreatedAt());
        binding.setDeleted(0L);
        binding.setVersion(0);
        var params = Map.of("bindings", java.util.List.of(binding));
        var statement = configuration.getMappedStatement(ProductBrandMapper.class.getName() + ".batchInsert");
        BoundSql insert = statement.getBoundSql(params);
        assertTrue(insert.getSql().contains("version"));
        assertEquals(9, insert.getParameterMappings().size());
        // 真正读取 foreach 实体属性并绑定 JDBC 参数，防止属性或集合名称拼错。
        new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, params, insert)
                .setParameters(org.mockito.Mockito.mock(java.sql.PreparedStatement.class));
        BoundSql delete = configuration.getMappedStatement(ProductBrandMapper.class.getName() + ".logicDeleteByCategoryId")
                .getBoundSql(Map.of("categoryId", 1L, "updatedBy", 0L));
        assertTrue(delete.getSql().contains("deleted = id"));
        assertTrue(delete.getSql().contains("version = version + 1"));
        assertTrue(delete.getSql().contains("NOW(3)"));
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/ProductBrandMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void combinedFilters_bindQueryPropertiesForBothStatements() throws Exception {
        Configuration configuration = configuration();
        BrandQueryDTO query = new BrandQueryDTO();
        query.setName("华");
        query.setInitial("H");
        query.setStatus(0);
        for (String method : new String[]{"countByQuery", "selectBrandPage"}) {
            BoundSql bound = configuration.getMappedStatement(ProductBrandMapper.class.getName() + "." + method)
                    .getBoundSql(Map.of("query", query, "offset", 20L, "limit", 10));
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("deleted = 0"));
            assertTrue(sql.contains("name LIKE CONCAT('%', ?, '%')"));
            assertTrue(sql.contains("initial = ?"));
            assertTrue(sql.contains("status = ?"));
            var properties = bound.getParameterMappings().stream().map(p -> p.getProperty()).toList();
            assertTrue(properties.containsAll(java.util.List.of("query.name", "query.initial", "query.status")));
            if (method.equals("selectBrandPage")) {
                assertTrue(sql.contains("ORDER BY sort_no ASC, id ASC"));
                assertTrue(sql.contains("LIMIT ? OFFSET ?"));
                assertEquals(java.util.List.of("query.name", "query.initial", "query.status", "limit", "offset"), properties);
            }
        }
    }

    @Test
    void noFilters_includesDisabledBrandsAndStillExcludesDeleted() throws Exception {
        BoundSql bound = configuration().getMappedStatement(ProductBrandMapper.class.getName() + ".countByQuery")
                .getBoundSql(Map.of("query", new BrandQueryDTO()));
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("deleted = 0"));
        assertFalse(sql.contains("status ="));
        assertTrue(bound.getParameterMappings().isEmpty());
    }
}
