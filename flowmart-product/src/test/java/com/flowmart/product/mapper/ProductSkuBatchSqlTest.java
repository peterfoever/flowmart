package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 XML 的 SQL 形状和 JDBC 参数测试，不证明 MySQL 执行与事务回滚。 */
class ProductSkuBatchSqlTest {
    @Test
    @DisplayName("批量SQL只有真实列，14个字段逐行绑定，JSON数组使用实际处理器")
    void batchInsert_bindsEveryColumnAndBothJsonRows() throws Exception {
        var config = new MybatisConfiguration();
        String resource = "mapper/ProductSkuMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        var value = new SkuSpecValueDTO(); value.setName("颜色"); value.setValue("红色");
        var rows = List.of(row(100L, List.of()), row(101L, List.of(value)));
        var method = ProductSkuMapper.class.getMethod("batchInsert", List.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(new Object[]{rows});
        var statement = config.getMappedStatement(ProductSkuMapper.class.getName() + ".batchInsert");
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertFalse(sql.contains("jsonb"));
        String columns = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')')).replaceAll("\\s+", "");
        assertEquals("id,spu_id,sku_code,spec_values,spec_hash,price,image_url,is_default,deleted,version,created_by,created_at,updated_by,updated_at", columns);
        assertEquals(28, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        var json = new ObjectMapper();
        for (int i = 0; i < rows.size(); i++) {
            int offset = i * 14;
            var row = rows.get(i);
            verify(jdbc).setLong(offset + 1, row.getId());
            verify(jdbc).setLong(offset + 2, 1L);
            verify(jdbc).setString(offset + 3, row.getSkuCode());
            var captured = ArgumentCaptor.forClass(String.class);
            verify(jdbc).setString(eq(offset + 4), captured.capture());
            assertEquals(json.valueToTree(row.getSpecValues()), json.readTree(captured.getValue()));
            verify(jdbc).setString(offset + 5, row.getSpecHash());
            verify(jdbc).setBigDecimal(offset + 6, row.getPrice());
            verify(jdbc).setString(offset + 7, "main.png");
            verify(jdbc).setBoolean(offset + 8, row.getIsDefault());
            verify(jdbc).setLong(offset + 9, 0L);
            verify(jdbc).setInt(offset + 10, 0);
            verify(jdbc).setLong(offset + 11, 42L);
            verify(jdbc).setObject(offset + 12, row.getCreatedAt());
            verify(jdbc).setLong(offset + 13, 42L);
            verify(jdbc).setObject(offset + 14, row.getUpdatedAt());
        }
    }

    private ProductSku row(long id, List<SkuSpecValueDTO> values) {
        var row = new ProductSku();
        row.setId(id); row.setSpuId(1L); row.setSkuCode("SKU" + id);
        row.setSpecValues(values); row.setSpecHash("a".repeat(63) + (id - 100));
        row.setPrice(new BigDecimal("99.90")); row.setImageUrl("main.png");
        row.setIsDefault(values.isEmpty()); row.setDeleted(0L); row.setVersion(0);
        row.setCreatedBy(42L); row.setUpdatedBy(42L);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 2, 12, 0)); row.setUpdatedAt(row.getCreatedAt());
        return row;
    }
}
