package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅测试实体映射；使用测试专用 Mapper，不提前实现业务 Mapper，也不连接数据库。 */
class ProductSkuMappingTest {
    interface MappingProbe extends BaseMapper<ProductSku> { }

    private MybatisConfiguration config;
    private static final String PREFIX = MappingProbe.class.getName() + ".";

    @BeforeEach
    void setUp() {
        config = new MybatisConfiguration();
        config.addMapper(MappingProbe.class);
    }

    private ResultMapping mapping(String property) {
        return config.getMappedStatement(PREFIX + "selectById").getResultMaps().getFirst()
                .getResultMappings().stream().filter(m -> property.equals(m.getProperty()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("SKU映射的表、字段类型、通用审计和版本字段正确")
    void entityMatchesSkuTable() {
        var table = TableInfoHelper.getTableInfo(ProductSku.class);
        assertEquals("product_sku", table.getTableName());
        assertTrue(table.isWithLogicDelete());
        assertTrue(table.isWithVersion());
        assertEquals(Long.class, mapping("spuId").getJavaType());
        assertEquals(String.class, mapping("specHash").getJavaType());
        assertEquals(Boolean.class, mapping("isDefault").getJavaType());
        assertEquals("is_default", mapping("isDefault").getColumn());
        assertEquals("spec_values", mapping("specValues").getColumn());
        assertEquals(Set.of("sku_code", "spu_id", "price", "spec_values", "spec_hash", "is_default",
                        "image_url", "created_by", "created_at", "updated_by", "updated_at", "deleted", "version"),
                table.getFieldList().stream().map(f -> f.getColumn()).collect(Collectors.toSet()));
        assertTrue(table.getFieldList().stream().anyMatch(f -> f.getProperty().equals("createdAt") && f.isWithInsertFill()));
        assertTrue(table.getFieldList().stream().anyMatch(f -> f.getProperty().equals("updatedAt") && f.isWithUpdateFill()));
    }

    @Test
    @DisplayName("SKU写入语句使用正确的JSON处理器和关联字段")
    void generatedInsert_usesJsonHandler() {
        var sku = new ProductSku();
        sku.setId(1L);
        sku.setSpuId(2L);
        sku.setSkuCode("SKU1");
        sku.setSpecValues(List.of());
        sku.setSpecHash("a".repeat(64));
        sku.setIsDefault(true);
        sku.setPrice(new BigDecimal("99.90"));
        sku.setImageUrl("a.png");
        var bound = config.getMappedStatement(PREFIX + "insert").getBoundSql(sku);
        assertTrue(bound.getSql().contains("product_sku"));
        assertTrue(bound.getSql().contains("spu_id"));
        var parameter = bound.getParameterMappings().stream()
                .filter(p -> p.getProperty().equals("specValues")).findFirst().orElseThrow();
        assertInstanceOf(JacksonTypeHandler.class, parameter.getTypeHandler());
    }

    @SuppressWarnings("unchecked")
    private List<SkuSpecValueDTO> roundTrip(List<SkuSpecValueDTO> values) throws Exception {
        var handler = (TypeHandler<List<SkuSpecValueDTO>>) mapping("specValues").getTypeHandler();
        var jdbc = mock(PreparedStatement.class);
        handler.setParameter(jdbc, 1, values, JdbcType.VARCHAR);
        var json = ArgumentCaptor.forClass(String.class);
        verify(jdbc).setString(eq(1), json.capture());
        var mapper = new ObjectMapper();
        assertEquals(mapper.valueToTree(values), mapper.readTree(json.getValue()));
        var result = mock(ResultSet.class);
        when(result.getString("spec_values")).thenReturn(json.getValue());
        return handler.getResult(result, "spec_values");
    }

    @Test
    @DisplayName("组合JSON往返保留中文、特殊字符、顺序和泛型类型")
    void specs_roundTripPreservesTypedValues() throws Exception {
        var color = new SkuSpecValueDTO();
        color.setName("颜色"); color.setValue("黑色/:\"特别款");
        var size = new SkuSpecValueDTO();
        size.setName("尺码"); size.setValue("M");
        var result = roundTrip(List.of(color, size));
        assertInstanceOf(SkuSpecValueDTO.class, ((List<?>) result).getFirst());
        assertEquals(List.of(color, size), result);
    }

    @Test
    @DisplayName("无规格SKU存储空JSON数组")
    void defaultSku_roundTripsEmptyArray() throws Exception {
        assertEquals(List.of(), roundTrip(List.of()));
    }
}
