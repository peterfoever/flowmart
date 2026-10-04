package com.flowmart.product.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.vo.SkuDetailVO;
import com.flowmart.product.vo.SkuListVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SkuDTOValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private SkuGenerateDTO generate(BigDecimal price) {
        var dto = new SkuGenerateDTO();
        dto.setPrice(price);
        return dto;
    }

    private SkuUpdateDTO update(BigDecimal price) {
        var dto = new SkuUpdateDTO();
        dto.setPrice(price);
        dto.setImageUrl("https://example.com/sku.png");
        dto.setVersion(0);
        return dto;
    }

    private Set<String> invalidFields(Object dto) {
        return validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "99.90", "99999999.99"})
    @DisplayName("生成和编辑允许零元及最高两位小数价格")
    void validPrice_passesBothContracts(String price) {
        assertTrue(invalidFields(generate(new BigDecimal(price))).isEmpty());
        assertTrue(invalidFields(update(new BigDecimal(price))).isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "100000000.00", "0.001", "1.230"})
    @DisplayName("生成和编辑拒绝空价格、负数、超上限及超过两位小数")
    void invalidPrice_rejectedByBothContracts(String price) {
        BigDecimal amount = price == null ? null : new BigDecimal(price);
        assertEquals(Set.of("price"), invalidFields(generate(amount)));
        assertEquals(Set.of("price"), invalidFields(update(amount)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("生成允许省略或空白图片，编辑不允许清空图片")
    void imageRequiredOnlyForUpdate(String image) {
        var generate = generate(BigDecimal.ZERO);
        generate.setImageUrl(image);
        assertTrue(invalidFields(generate).isEmpty());
        var update = update(BigDecimal.ZERO);
        update.setImageUrl(image);
        assertEquals(Set.of("imageUrl"), invalidFields(update));
    }

    @ParameterizedTest
    @ValueSource(ints = {512, 513})
    @DisplayName("生成和编辑图片长度上限均为512")
    void imageLengthBoundary(int length) {
        var generate = generate(BigDecimal.ZERO);
        generate.setImageUrl("a".repeat(length));
        var update = update(BigDecimal.ZERO);
        update.setImageUrl(generate.getImageUrl());
        Set<String> expected = length == 512 ? Set.of() : Set.of("imageUrl");
        assertEquals(expected, invalidFields(generate));
        assertEquals(expected, invalidFields(update));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1})
    @DisplayName("编辑版本号必须存在且非负")
    void invalidVersion_rejected(Integer version) {
        var dto = update(BigDecimal.ZERO);
        dto.setVersion(version);
        assertEquals(Set.of("version"), invalidFields(dto));
    }

    @Test
    @DisplayName("生成请求只接收统一价格和可选图片")
    void generateJson_hasNoClientSuppliedCombinations() throws Exception {
        var json = new ObjectMapper();
        var dto = json.readValue("{\"price\":99.90,\"imageUrl\":\"a.png\"}", SkuGenerateDTO.class);
        assertEquals(new BigDecimal("99.90"), dto.getPrice());
        var node = json.valueToTree(dto);
        assertEquals(2, node.size());
        assertTrue(node.has("price"));
        assertTrue(node.has("imageUrl"));
        assertFalse(node.has("items"));
    }

    @Test
    @DisplayName("列表和详情使用数组组合及一致的默认标记，不暴露范围外字段")
    void responseContracts_keepDefaultSkuAsEmptyArray() {
        var list = new SkuListVO();
        list.setSpecValues(List.of());
        list.setIsDefault(true);
        list.setImageUrl("a.png");
        var detail = new SkuDetailVO();
        detail.setSpecValues(List.of());
        detail.setIsDefault(true);
        detail.setImageUrl("a.png");
        var json = new ObjectMapper();
        for (Object vo : List.of(list, detail)) {
            var node = json.valueToTree(vo);
            assertTrue(node.get("specValues").isArray());
            assertEquals(0, node.get("specValues").size());
            assertTrue(node.get("isDefault").asBoolean());
            assertEquals("a.png", node.get("imageUrl").asText());
            for (String field : List.of("marketPrice", "stock", "status", "specHash")) {
                assertFalse(node.has(field));
            }
        }
    }
}
