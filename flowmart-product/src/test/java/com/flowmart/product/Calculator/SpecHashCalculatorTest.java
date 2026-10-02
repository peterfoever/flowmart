package com.flowmart.product.Calculator;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.dto.SkuSpecValueDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;

public class SpecHashCalculatorTest {
    @Test
    void noSpec_returnsFixedText() {
        assertEquals("{}", SpecHashCalculator.canonicalize(null));
        assertEquals("{}", SpecHashCalculator.canonicalize(List.of()));
    }

    @Test
    void orderDoesNotMatter() {
        var a = List.of(new SkuSpecValueDTO("颜色", "红色"),
                new SkuSpecValueDTO("尺寸", "XL"));
        var b = List.of(new SkuSpecValueDTO("尺寸", "XL"),
                new SkuSpecValueDTO("颜色", "红色"));
        assertEquals(SpecHashCalculator.calculate(a), SpecHashCalculator.calculate(b));
    }

    @Test
    void duplicateName_afterTrim_throws() {
        var list = List.of(new SkuSpecValueDTO("颜色", "红色"),
                new SkuSpecValueDTO(" 颜色 ", "蓝色"));
        var ex = assertThrows(BizException.class,
                () -> SpecHashCalculator.calculate(list));
        assertEquals("SPEC_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void duplicateName_notCoveredByMap() {
        // 如果实现里先 toMap，这里会因为 key 冲突被覆盖，检测不到重复
        var list = List.of(new SkuSpecValueDTO("颜色", "红色"),
                new SkuSpecValueDTO("颜色", "蓝色"));
        var ex = assertThrows(BizException.class,
                () -> SpecHashCalculator.calculate(list));
        assertEquals("SPEC_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void lengthPrefix_isUtf8ByteLength() {
        // "颜色" 是 2 个 char，但 6 个 UTF-8 字节
        var list = List.of(new SkuSpecValueDTO("颜色", "红色"));
        String canonical = SpecHashCalculator.canonicalize(list);
        assertTrue(canonical.startsWith("6:颜色6:红色"), "实际：" + canonical);
    }

    @Test
    void fullWidthSpace_trimmedAtBothEnds() {
        var list = List.of(new SkuSpecValueDTO("\u3000颜色\u3000", "\u3000红色\u3000"));
        String canonical = SpecHashCalculator.canonicalize(list);
        assertEquals("6:颜色6:红色", canonical);
    }

    @Test
    void innerWhitespace_preserved() {
        var a = List.of(new SkuSpecValueDTO("颜色", "红  色"));
        var b = List.of(new SkuSpecValueDTO("颜色", "红 色"));
        assertNotEquals(SpecHashCalculator.calculate(a), SpecHashCalculator.calculate(b));
    }

    @Test
    void doesNotModifyInputList() {
        var list = new ArrayList<>(List.of(
                new SkuSpecValueDTO("尺寸", "XL"),
                new SkuSpecValueDTO("颜色", "红色")));
        var snapshot = list.stream().map(d -> d.getName() + "=" + d.getValue()).toList();
        SpecHashCalculator.calculate(list);
        var after = list.stream().map(d -> d.getName() + "=" + d.getValue()).toList();
        assertEquals(snapshot, after);
    }

    @Test
    void nfc_notApplied() {
        var precomposed = List.of(new SkuSpecValueDTO("颜色", "é"));          // U+00E9
        var decomposed = List.of(new SkuSpecValueDTO("颜色", "e\u0301"));      // e + 重音
        assertNotEquals(SpecHashCalculator.calculate(precomposed),
                SpecHashCalculator.calculate(decomposed));
    }
}
