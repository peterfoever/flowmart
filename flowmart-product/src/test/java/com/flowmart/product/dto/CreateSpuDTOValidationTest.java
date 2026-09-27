package com.flowmart.product.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.validation.Validator;
import java.util.*;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreateSpuDTO / SpecDTO 校验测试
 * <p>
 * 使用 Validator 手动触发注解校验，不依赖 Spring MVC
 */
class CreateSpuDTOValidationTest {

    private jakarta.validation.Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
         validator = factory.getValidator();
    }

    // ============================================================
    // null 校验
    // ============================================================

    @Test
    void name_null_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setName(null);
        assertHasViolation(dto, "name");
    }

    @Test
    void categoryId_null_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setCategoryId(null);
        assertHasViolation(dto, "categoryId");
    }

    @Test
    void mainImageUrl_null_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setMainImageUrl(null);
        assertHasViolation(dto, "mainImageUrl");
    }

    @Test
    void brandId_null_passes() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setBrandId(null);  // 无品牌是合法业务场景
        assertNoViolations(dto);
    }

    // ✅ 修正：carouselImages = null 应该失败
    @Test
    void carouselImages_null_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setCarouselImages(null);
        assertHasViolation(dto, "carouselImages");
    }

    // ✅ 修正：specs = null 应该失败
    @Test
    void specs_null_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(null);
        assertHasViolation(dto, "specs");
    }

    // ============================================================
    // 空数组校验（合法）
    // ============================================================

    @Test
    void carouselImages_emptyArray_passes() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setCarouselImages(Collections.emptyList());
        assertNoViolations(dto);
    }

    @Test
    void specs_emptyArray_passes() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Collections.emptyList());
        assertNoViolations(dto);
    }

    // ============================================================
    // 规格 null 元素
    // ============================================================

    @Test
    void specs_containsNullElement_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Arrays.asList(buildSpec("颜色", 3), null));
        assertHasViolation(dto, "specs[1]");
    }

    @Test
    void specValues_containsNullElement_fails() {
        CreateSpuDTO dto = buildValidDTO();
        SpecDTO spec = new SpecDTO();
        spec.setName("颜色");
        spec.setValues(Arrays.asList("红色", null, "蓝色"));
        dto.setSpecs(Collections.singletonList(spec));
        assertHasViolation(dto, "specs[0].values[1]");
    }

    // ============================================================
    // 规格数量边界：3 通过，4 失败
    // ============================================================

    @Test
    void specs_threeSpecs_passes() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Arrays.asList(
                buildSpec("颜色", 3),
                buildSpec("尺码", 3),
                buildSpec("材质", 3)
        ));
        assertNoViolations(dto);
    }

    @Test
    void specs_fourSpecs_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Arrays.asList(
                buildSpec("颜色", 3),
                buildSpec("尺码", 3),
                buildSpec("材质", 3),
                buildSpec("风格", 3)
        ));
        assertHasViolation(dto, "specs");
    }

    // ============================================================
    // 单规格值数量边界：20 通过，21 失败
    // ============================================================

    @Test
    void specValues_twentyValues_passes() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Collections.singletonList(
                buildSpec("颜色", 20)
        ));
        assertNoViolations(dto);
    }

    // ✅ 修正后：SpecDTO.values 上限已改为 20，21 个会失败
    @Test
    void specValues_twentyOneValues_fails() {
        CreateSpuDTO dto = buildValidDTO();
        dto.setSpecs(Collections.singletonList(
                buildSpec("颜色", 21)
        ));
        assertHasViolation(dto, "specs[0].values");
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private CreateSpuDTO buildValidDTO() {
        CreateSpuDTO dto = new CreateSpuDTO();
        dto.setName("测试商品");
        dto.setCategoryId(1L);
        dto.setBrandId(2L);
        dto.setMainImageUrl("https://example.com/main.jpg");
        // ✅ 修正：合法 DTO 必须传空数组，不能传 null
        dto.setCarouselImages(Collections.emptyList());
        dto.setSpecs(Collections.emptyList());
        return dto;
    }

    private SpecDTO buildSpec(String name, int valueCount) {
        SpecDTO spec = new SpecDTO();
        spec.setName(name);
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= valueCount; i++) {
            values.add("值" + i);
        }
        spec.setValues(values);
        return spec;
    }

    private void assertNoViolations(CreateSpuDTO dto) {
        Set<ConstraintViolation<CreateSpuDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(),
                "期望无校验错误，实际: " + formatViolations(violations));
    }

    private void assertHasViolation(CreateSpuDTO dto, String expectedProperty) {
        Set<ConstraintViolation<CreateSpuDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "期望有校验错误，实际无");
        boolean found = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().startsWith(expectedProperty));
        assertTrue(found,
                "期望属性 " + expectedProperty + " 有错误，实际: " + formatViolations(violations));
    }

    private String formatViolations(Set<ConstraintViolation<CreateSpuDTO>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + " -> " + v.getMessage())
                .collect(Collectors.joining(", "));
    }
}
