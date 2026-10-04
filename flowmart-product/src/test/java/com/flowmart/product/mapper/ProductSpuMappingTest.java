package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.ProductSpu;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 加载实际 Mapper XML 和 MP 生成语句；使用 JDBC mock，不连接数据库。 */
class ProductSpuMappingTest {
    private MybatisConfiguration configuration;
    private static final String PREFIX = ProductSpuMapper.class.getName() + ".";

    @BeforeEach
    void setUp() throws Exception {
        configuration = new MybatisConfiguration();
        String resource = "mapper/ProductSpuMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private ResultMapping mapping(String property) {
        return configuration.getMappedStatement(PREFIX + "selectById").getResultMaps().get(0)
                .getResultMappings().stream().filter(m -> property.equals(m.getProperty()))
                .findFirst().orElseThrow();
    }

    @Test
    void baseMapper_usesEntityJsonMappings() {
        assertEquals("carousel_images", mapping("carouselImages").getColumn());
        assertEquals("spec_json", mapping("specs").getColumn());
        assertInstanceOf(JacksonTypeHandler.class, mapping("carouselImages").getTypeHandler());
        assertInstanceOf(JacksonTypeHandler.class, mapping("specs").getTypeHandler());
        assertEquals("main_image_url", mapping("mainImageUrl").getColumn());
        assertEquals("spu_code", mapping("spuCode").getColumn());
    }

    @Test
    void baseEntity_metadataRetainsAuditLogicDeleteAndVersion() {
        var table = TableInfoHelper.getTableInfo(ProductSpu.class);
        assertEquals("product_spu", table.getTableName());
        assertEquals(IdType.ASSIGN_ID, table.getIdType());
        assertTrue(table.isWithLogicDelete());
        assertTrue(table.isWithVersion());
        assertEquals("deleted", table.getLogicDeleteFieldInfo().getColumn());
        assertEquals("version", table.getVersionFieldInfo().getColumn());
        var created = table.getFieldList().stream().filter(f -> f.getProperty().equals("createdAt")).findFirst().orElseThrow();
        var updated = table.getFieldList().stream().filter(f -> f.getProperty().equals("updatedAt")).findFirst().orElseThrow();
        assertTrue(created.isWithInsertFill());
        assertTrue(updated.isWithUpdateFill());
        String sql = configuration.getMappedStatement(PREFIX + "selectById").getBoundSql(1L).getSql();
        assertTrue(sql.replaceAll("\\s+", "").contains("deleted=0"));
    }

    @Test
    void generatedInsert_hasJsonHandlersAndCorrectColumnNames() {
        ProductSpu spu = new ProductSpu();
        spu.setId(1L);
        spu.setSpuCode("SPU1");
        spu.setName("手机");
        spu.setCategoryId(2L);
        spu.setMainImageUrl("https://example.com/main.png");
        spu.setCarouselImages(List.of());
        spu.setSpecs(List.of());
        var bound = configuration.getMappedStatement(PREFIX + "insert").getBoundSql(spu);
        assertTrue(bound.getSql().contains("spec_json"));
        assertTrue(bound.getSql().contains("carousel_images"));
        for (String property : List.of("carouselImages", "specs")) {
            var parameter = bound.getParameterMappings().stream()
                    .filter(p -> p.getProperty().equals(property)).findFirst().orElseThrow();
            assertInstanceOf(JacksonTypeHandler.class, parameter.getTypeHandler());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(String property, String column, T value, String expectedJson) throws Exception {
        TypeHandler<T> handler = (TypeHandler<T>) mapping(property).getTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setParameter(ps, 1, value, JdbcType.VARCHAR);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(1), json.capture());
        ObjectMapper jackson = new ObjectMapper();
        assertEquals(jackson.readTree(expectedJson), jackson.readTree(json.getValue()));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(column)).thenReturn(json.getValue());
        return handler.getResult(rs, column);
    }

    @Test
    void carouselImages_roundTripPreservesOrderAndChinese() throws Exception {
        List<String> images = List.of("https://example.com/黑色.png", "https://example.com/白色.png");
        assertEquals(images, roundTrip("carouselImages", "carousel_images", images,
                "[\"https://example.com/黑色.png\",\"https://example.com/白色.png\"]"));
    }

    @Test
    void specs_roundTripRetainsGenericElementType() throws Exception {
        SpecDTO spec = new SpecDTO();
        spec.setName("颜色");
        spec.setValues(List.of("黑色", "白色"));
        List<SpecDTO> result = roundTrip("specs", "spec_json", List.of(spec),
                "[{\"name\":\"颜色\",\"values\":[\"黑色\",\"白色\"]}]");
        assertInstanceOf(SpecDTO.class, ((List<?>) result).get(0));
        assertEquals(List.of(spec), result);
    }

    @Test
    void emptyLists_areJsonArraysNotStringsOrNull() throws Exception {
        assertEquals(List.of(), roundTrip("carouselImages", "carousel_images", List.of(), "[]"));
        assertEquals(List.of(), roundTrip("specs", "spec_json", List.of(), "[]"));
    }

    @Test
    void malformedJson_isNotSilentlyConvertedToEmptyList() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("spec_json")).thenReturn("{broken");
        assertThrows(Exception.class, () -> mapping("specs").getTypeHandler().getResult(rs, "spec_json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("锁定查询使用完整实体映射，保留规格和轮播图的泛型处理器")
    void lockedQuery_returnsCompleteEntityWithTypedJson() throws Exception {
        var statement = configuration.getMappedStatement(PREFIX + "selectByIdForUpdate");
        var resultMap = statement.getResultMaps().getFirst();
        assertSame(configuration.getMappedStatement(PREFIX + "selectById").getResultMaps().getFirst(), resultMap);
        var method = ProductSpuMapper.class.getMethod("selectByIdForUpdate", Long.class);
        Object params = new org.apache.ibatis.reflection.ParamNameResolver(configuration, method)
                .getNamedParams(new Object[]{123L});
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.endsWith("WHERE id = ? AND deleted = 0 FOR UPDATE"));
        var columns = java.util.Arrays.stream(sql.substring("SELECT ".length(), sql.indexOf(" FROM ")).split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toSet());
        var table = TableInfoHelper.getTableInfo(ProductSpu.class);
        var expectedColumns = new java.util.HashSet<>(table.getFieldList().stream().map(f -> f.getColumn()).toList());
        expectedColumns.add("id");
        assertEquals(expectedColumns, columns);
        var jdbc = mock(PreparedStatement.class);
        new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, params, bound).setParameters(jdbc);
        verify(jdbc).setLong(1, 123L);
        // resultMap 与 selectById 同一个对象，现有 roundTrip 测试同时覆盖该锁定查询的泛型映射。
        specs_roundTripRetainsGenericElementType();
        carouselImages_roundTripPreservesOrderAndChinese();
    }
}
