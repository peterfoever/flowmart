package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 检查项目真实 XML 及参数绑定；不声称执行了数据库更新或并发竞争。 */
class ProductSkuUpdateSqlTest {
    @Test
    @DisplayName("更新SQL仅修改白名单字段，带归属、未删除和请求版本条件，数据库版本自增一次")
    void updateSql_whitelistsColumnsAndBindsClientVersion() throws Exception {
        var configuration = new MybatisConfiguration();
        String resource = "mapper/ProductSkuMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var method = ProductSkuMapper.class.getMethod("updatePriceAndImage", Long.class, Long.class,
                Integer.class, BigDecimal.class, String.class, Long.class, LocalDateTime.class);
        var now = LocalDateTime.of(2026, 10, 4, 15, 0);
        var price = new BigDecimal("129.90");
        var params = new ParamNameResolver(configuration, method).getNamedParams(
                new Object[]{100L, 42L, 3, price, "sku.png", 0L, now});
        var statement = configuration.getMappedStatement(ProductSkuMapper.class.getName() + ".updatePriceAndImage");
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", "");
        assertEquals("UPDATEproduct_skuSETprice=?,image_url=?,updated_by=?,updated_at=?,version=version+1"
                + "WHEREid=?ANDspu_id=?ANDdeleted=0ANDversion=?", sql);
        assertEquals(7, bound.getParameterMappings().size());
        var jdbc = mock(PreparedStatement.class);
        new DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setBigDecimal(1, price);
        verify(jdbc).setString(2, "sku.png");
        verify(jdbc).setLong(3, 0L);
        verify(jdbc).setObject(4, now);
        verify(jdbc).setLong(5, 100L);
        verify(jdbc).setLong(6, 42L);
        verify(jdbc).setInt(7, 3);
    }
}
