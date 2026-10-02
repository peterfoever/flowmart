package com.flowmart.product.generator;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.common.exception.ErrorCode;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.enums.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SpecCombinationGeneratorTest {
    private static SpecDTO spec(String name, List<String> values) {
        var dto = new SpecDTO();
        dto.setName(name);
        dto.setValues(values);
        return dto;
    }

    private static SkuSpecValueDTO selected(String name, String value) {
        var dto = new SkuSpecValueDTO();
        dto.setName(name);
        dto.setValue(value);
        return dto;
    }

    private static List<String> values(int n) {
        return IntStream.range(0, n).mapToObj(i -> "V" + i).toList();
    }

    private static void assertError(ErrorCode expected, List<SpecDTO> input) {
        var error = assertThrows(BizException.class, () -> SpecCombinationGenerator.generate(input));
        assertEquals(expected.getCode(), error.getCode());
    }

    @Test
    @DisplayName("无规格返回一个默认空组合，而非零个组合")
    void noSpec_returnsSingleEmptyCombination() {
        assertEquals(List.of(List.of()), SpecCombinationGenerator.generate(List.of()));
    }

    @Test
    @DisplayName("null列表或null规格项拒绝，不静默生成默认SKU")
    void nullDefinitionOrDimension_rejected() {
        assertError(CommonErrorCode.PARAM_INVALID, null);
        assertError(CommonErrorCode.PARAM_INVALID, Arrays.asList((SpecDTO) null));
    }

    @Test
    @DisplayName("2乘3得到六个完整组合且最后一个维度变化最快")
    void twoByThree_returnsSixOrderedCombinations() {
        var result = SpecCombinationGenerator.generate(List.of(
                spec("颜色", List.of("红", "蓝")), spec("尺寸", List.of("S", "M", "L"))));
        var expected = new ArrayList<List<SkuSpecValueDTO>>();
        for (String color : List.of("红", "蓝")) {
            for (String size : List.of("S", "M", "L")) {
                expected.add(List.of(selected("颜色", color), selected("尺寸", size)));
            }
        }
        assertEquals(expected, result);
        assertEquals(6, result.stream().distinct().count());
    }

    @Test
    @DisplayName("单维度规范化后保持值顺序")
    void singleDimension_preservesOrderAfterTrimming() {
        assertEquals(List.of(List.of(selected("颜色", "红")), List.of(selected("颜色", "蓝"))),
                SpecCombinationGenerator.generate(List.of(spec(" 颜色 ", List.of(" 红 ", "蓝")))));
    }

    @Test
    @DisplayName("恰好1000个组合全部生成且无重复")
    void exactly1000_allowed() {
        var result = SpecCombinationGenerator.generate(List.of(
                spec("A", values(10)), spec("B", values(10)), spec("C", values(10))));
        assertEquals(1000, result.size());
        assertEquals(1000, result.stream().distinct().count());
        assertTrue(result.stream().allMatch(c -> c.size() == 3));
        assertEquals(List.of(selected("A", "V9"), selected("B", "V9"), selected("C", "V9")), result.getLast());
    }

    @Test
    @DisplayName("7乘11乘13恰好1001，超过上限1个也拒绝")
    void exactly1001_rejected() {
        assertError(ProductErrorCode.TOO_MANY_COMBINATIONS,
                List.of(spec("A", values(7)), spec("B", values(11)), spec("C", values(13))));
    }

    @Test
    @DisplayName("20乘20乘20超过组合数量限制")
    void maximumSpuDefinition_rejected() {
        assertError(ProductErrorCode.TOO_MANY_COMBINATIONS,
                List.of(spec("A", values(20)), spec("B", values(20)), spec("C", values(20))));
    }

    @Test
    @DisplayName("空或null候选值列表使用明确业务错误码")
    void emptyOrNullValues_rejected() {
        assertError(ProductErrorCode.SPEC_VALUES_EMPTY, List.of(spec("颜色", List.of())));
        assertError(ProductErrorCode.SPEC_VALUES_EMPTY, List.of(spec("颜色", null)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    @DisplayName("null或空白规格名被拒绝")
    void blankName_rejected(String name) {
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec(name, List.of("红"))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    @DisplayName("null或空白规格值返回业务异常而非空指针")
    void blankValue_rejected(String value) {
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec("颜色", Arrays.asList("红", value))));
    }

    @Test
    @DisplayName("trim后规格名重复必须拒绝，不能被Map覆盖")
    void duplicateNames_rejected() {
        assertError(ProductErrorCode.SPEC_NAME_DUPLICATE,
                List.of(spec("颜色", List.of("红")), spec(" 颜色 ", List.of("蓝"))));
    }

    @Test
    @DisplayName("同维度trim后重复值必须拒绝而非生成重复SKU")
    void duplicateValues_rejected() {
        assertError(ProductErrorCode.SPEC_VALUE_DUPLICATE, List.of(spec("颜色", List.of("红", " 红 "))));
    }

    @Test
    @DisplayName("不同维度允许相同规格值，特殊符号不被拆分")
    void sameValueAcrossDimensions_allowed() {
        String value = "黑色/:\"特别款";
        assertEquals(List.of(List.of(selected("外层", value), selected("内层", value))),
                SpecCombinationGenerator.generate(List.of(spec("外层", List.of(value)), spec("内层", List.of(value)))));
    }

    @Test
    @DisplayName("超过SPU的3维度或每维20值约束时拒绝")
    void tooManyDimensionsOrValues_rejected() {
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec("A", values(1)), spec("B", values(1)),
                spec("C", values(1)), spec("D", values(1))));
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec("A", values(21))));
        assertEquals(20, SpecCombinationGenerator.generate(List.of(spec("A", values(20)))).size());
    }

    @Test
    @DisplayName("规格名32字符、规格值64字符边界与模型一致")
    void textLengthBoundaries() {
        assertEquals(1, SpecCombinationGenerator.generate(List.of(spec("名".repeat(32), List.of("值".repeat(64))))).size());
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec("名".repeat(33), List.of("值"))));
        assertError(CommonErrorCode.PARAM_INVALID, List.of(spec("名", List.of("值".repeat(65)))));
    }

    @Test
    @DisplayName("规范化不修改输入，输入后来修改也不影响已有输出")
    void inputAndOutputAreIndependent() {
        var colors = new ArrayList<>(List.of(" 红 ", "蓝"));
        var color = spec(" 颜色 ", colors);
        var input = new ArrayList<>(List.of(color));
        var result = SpecCombinationGenerator.generate(input);
        assertEquals(" 颜色 ", color.getName());
        assertEquals(List.of(" 红 ", "蓝"), colors);
        color.setName("新名称");
        colors.set(0, "绿");
        input.clear();
        assertEquals(List.of(selected("颜色", "红")), result.getFirst());
    }

    @Test
    @DisplayName("每个组合和DTO独立，修改一个输出项不污染其他组合或输入")
    void combinationsAreIndependent() {
        var input = List.of(spec("颜色", List.of("红", "蓝")), spec("尺寸", List.of("S", "M")));
        var result = SpecCombinationGenerator.generate(input);
        var first = result.get(0);
        var second = result.get(1);
        assertNotSame(first, second);
        assertNotSame(first.get(0), second.get(0));
        first.get(0).setValue("绿");
        first.get(0).setName("修改过的名称");
        assertEquals(selected("颜色", "红"), second.get(0));
        assertEquals("颜色", input.get(0).getName());
        assertEquals(List.of("红", "蓝"), input.get(0).getValues());
        assertEquals(selected("颜色", "红"), SpecCombinationGenerator.generate(input).get(0).get(0));
    }

    @Test
    @DisplayName("默认和非默认组合的两层列表均不可增删")
    void returnedListsAreUnmodifiable() {
        for (var result : List.of(SpecCombinationGenerator.generate(List.of()),
                SpecCombinationGenerator.generate(List.of(spec("颜色", List.of("红")))))) {
            assertThrows(UnsupportedOperationException.class, () -> result.add(List.of()));
            assertThrows(UnsupportedOperationException.class, () -> result.get(0).add(selected("x", "y")));
        }
    }
}
