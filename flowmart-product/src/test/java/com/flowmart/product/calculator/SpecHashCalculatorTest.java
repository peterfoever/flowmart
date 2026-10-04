package com.flowmart.product.calculator;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.dto.SkuSpecValueDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;

public class SpecHashCalculatorTest {
    private static SkuSpecValueDTO item(String name, String value) {
        var dto = new SkuSpecValueDTO();
        dto.setName(name);
        dto.setValue(value);
        return dto;
    }

    @Test
    @DisplayName("默认SKU编码为空字节序列并生成已知SHA-256摘要")
    void noSpec_returnsEmptyEncoding() {
        assertEquals("", SpecHashCalculator.canonicalize(List.of()));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                SpecHashCalculator.calculate(List.of()));
    }

    @Test
    void orderDoesNotMatter() {
        var a = List.of(item("颜色", "红色"), item("尺寸", "XL"));
        var b = List.of(item("尺寸", "XL"), item("颜色", "红色"));
        assertEquals(SpecHashCalculator.calculate(a), SpecHashCalculator.calculate(b));
        assertEquals("6:尺寸2:XL6:颜色6:红色", SpecHashCalculator.canonicalize(a));
        assertTrue(SpecHashCalculator.calculate(a).matches("[0-9a-f]{64}"));
    }

    @Test
    void duplicateName_afterTrim_throws() {
        var list = List.of(item("颜色", "红色"), item(" 颜色 ", "蓝色"));
        var ex = assertThrows(BizException.class,
                () -> SpecHashCalculator.calculate(list));
        assertEquals(ProductErrorCode.SPEC_NAME_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void duplicateName_notCoveredByMap() {
        // 如果实现里先 toMap，这里会因为 key 冲突被覆盖，检测不到重复
        var list = List.of(item("颜色", "红色"), item("颜色", "蓝色"));
        var ex = assertThrows(BizException.class,
                () -> SpecHashCalculator.calculate(list));
        assertEquals(ProductErrorCode.SPEC_NAME_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void lengthPrefix_isUtf8ByteLength() {
        // "颜色" 是 2 个 char，但 6 个 UTF-8 字节
        var list = List.of(item("颜色", "红色"));
        String canonical = SpecHashCalculator.canonicalize(list);
        assertEquals("6:颜色6:红色", canonical);
    }

    @Test
    void fullWidthSpace_trimmedAtBothEnds() {
        var list = List.of(item("\u3000颜色\u3000", "\u3000红色\u3000"));
        String canonical = SpecHashCalculator.canonicalize(list);
        assertEquals("6:颜色6:红色", canonical);
    }

    @Test
    void innerWhitespace_preserved() {
        var a = List.of(item("颜色", "红  色"));
        var b = List.of(item("颜色", "红 色"));
        assertNotEquals(SpecHashCalculator.calculate(a), SpecHashCalculator.calculate(b));
    }

    @Test
    void doesNotModifyInputList() {
        var color = item(" 颜色 ", " 红色 ");
        var size = item(" 尺寸 ", " XL ");
        var list = new ArrayList<>(List.of(color, size));
        var snapshot = list.stream().map(d -> d.getName() + "=" + d.getValue()).toList();
        SpecHashCalculator.calculate(list);
        var after = list.stream().map(d -> d.getName() + "=" + d.getValue()).toList();
        assertEquals(snapshot, after);
        assertSame(color, list.get(0));
        assertSame(size, list.get(1));
        assertEquals("6:尺寸2:XL6:颜色6:红色", SpecHashCalculator.canonicalize(list));
    }

    @Test
    void nfc_notApplied() {
        var precomposed = List.of(item("颜色", "é"));          // U+00E9
        var decomposed = List.of(item("颜色", "e\u0301"));      // e + 重音
        assertNotEquals(SpecHashCalculator.calculate(precomposed),
                SpecHashCalculator.calculate(decomposed));
    }

    @Test
    @DisplayName("null列表和null组合项返回参数异常而非默认哈希或空指针")
    void nullInput_rejected() {
        assertInvalid(null);
        assertInvalid(Arrays.asList((SkuSpecValueDTO) null));
        assertInvalid(Arrays.asList(item("颜色", "红"), null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "\u3000"})
    @DisplayName("null及空白名称或值在排序编码前拒绝")
    void blankFields_rejected(String blank) {
        assertInvalid(List.of(item(blank, "红")));
        assertInvalid(List.of(item("颜色", blank)));
    }

    @Test
    @DisplayName("emoji按UTF-8四字节编码而非两个UTF-16代码单元")
    void emoji_usesByteLength() {
        assertEquals("4:😀4:🚀", SpecHashCalculator.canonicalize(List.of(item("😀", "🚀"))));
    }

    @Test
    @DisplayName("ASCII大小写不被统一")
    void caseIsPreserved() {
        assertNotEquals(SpecHashCalculator.calculate(List.of(item("color", "Red"))),
                SpecHashCalculator.calculate(List.of(item("color", "red"))));
        assertNotEquals(SpecHashCalculator.calculate(List.of(item("Color", "red"))),
                SpecHashCalculator.calculate(List.of(item("color", "red"))));
    }

    @Test
    @DisplayName("长度前缀区分含冒号数字的不同字段边界")
    void delimiterInFields_isUnambiguous() {
        var first = List.of(item("a:1", "b"));
        var second = List.of(item("a", "1:b"));
        assertEquals("3:a:11:b", SpecHashCalculator.canonicalize(first));
        assertEquals("1:a3:1:b", SpecHashCalculator.canonicalize(second));
        assertNotEquals(SpecHashCalculator.calculate(first), SpecHashCalculator.calculate(second));
        assertEquals("1:k5:a\"\\\nb", SpecHashCalculator.canonicalize(List.of(item("k", "a\"\\\nb"))));
    }

    @Test
    @DisplayName("重复名称校验也覆盖全角首尾空格")
    void duplicateFullWidthTrimmedName_rejected() {
        var error = assertThrows(BizException.class, () -> SpecHashCalculator.calculate(
                List.of(item("颜色", "红"), item("\u3000颜色\u3000", "蓝"))));
        assertEquals(ProductErrorCode.SPEC_NAME_DUPLICATE.getCode(), error.getCode());
    }

    private static void assertInvalid(List<SkuSpecValueDTO> values) {
        var error = assertThrows(BizException.class, () -> SpecHashCalculator.calculate(values));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), error.getCode());
    }
}
