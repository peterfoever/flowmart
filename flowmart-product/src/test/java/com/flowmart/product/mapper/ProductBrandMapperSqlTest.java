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
