package com.flowmart.product.generator;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
public class SpecCombinationGeneratorTest {
    // ── 规则 1：无规格 → 一个空组合 ─────────────────────────────────
    @Test
    void noSpec_returnsSingleEmptyCombination() {
        List<Map<String, String>> result = SpecCombinationGenerator.generate(null);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isEmpty());

        result = SpecCombinationGenerator.generate(Map.of());
        assertEquals(1, result.size());
        assertTrue(result.get(0).isEmpty());
    }

    // ── 规则 2：2×3 → 六个不同组合 ─────────────────────────────────
    @Test
    void twoByThree_returnsSixCombinations() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("颜色", List.of("红", "蓝"));
        spec.put("尺寸", List.of("S", "M", "L"));

        List<Map<String, String>> result = SpecCombinationGenerator.generate(spec);

        assertEquals(6, result.size());
        // 验证每一种组合都不同
        assertEquals(6, result.stream().map(Map::toString).distinct().count());
        // 验证笛卡尔积内容
        assertTrue(result.contains(Map.of("颜色", "红", "尺寸", "S")));
        assertTrue(result.contains(Map.of("颜色", "红", "尺寸", "M")));
        assertTrue(result.contains(Map.of("颜色", "红", "尺寸", "L")));
        assertTrue(result.contains(Map.of("颜色", "蓝", "尺寸", "S")));
        assertTrue(result.contains(Map.of("颜色", "蓝", "尺寸", "M")));
        assertTrue(result.contains(Map.of("颜色", "蓝", "尺寸", "L")));
    }

    // ── 规则 3：恰好 1,000 个允许 ──────────────────────────────────
    @Test
    void exactly1000_allowed() {
        // 10 × 10 × 10 = 1000
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("A", buildValues(10));
        spec.put("B", buildValues(10));
        spec.put("C", buildValues(10));

        List<Map<String, String>> result = SpecCombinationGenerator.generate(spec);
        assertEquals(1000, result.size());
    }

    @Test
    void over1000_rejected() {
        // 10 × 10 × 11 = 1100 > 1000
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("A", buildValues(10));
        spec.put("B", buildValues(10));
        spec.put("C", buildValues(11));


    }

    // ── 规则 4：任一规格值列表为空则拒绝 ────────────────────────────
    @Test
    void emptyValueList_rejected() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("颜色", List.of("红", "蓝"));
        spec.put("尺寸", List.of());


    }

    @Test
    void blankValue_rejected() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("颜色", List.of("红", "  "));


    }

    // ── 规则 5：不修改输入，各组合之间不共享可变 Map ─────────────────
    @Test
    void doesNotModifyInput() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        List<String> colors = new java.util.ArrayList<>(List.of("红", "蓝"));
        spec.put("颜色", colors);

        SpecCombinationGenerator.generate(spec);

        // 原输入仍为两色，没有被清空或追加
        assertEquals(List.of("红", "蓝"), spec.get("颜色"));
    }

    @Test
    void combinationsAreIndependent() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("颜色", List.of("红", "蓝"));
        spec.put("尺寸", List.of("S", "M"));

        List<Map<String, String>> result = SpecCombinationGenerator.generate(spec);

        // 每个组合都是独立对象，修改其中一个不影响其他
        Map<String, String> first = new LinkedHashMap<>(result.get(0));
        first.put("颜色", "绿");
        assertEquals("红", result.get(0).get("颜色"));
    }

    @Test
    void returnedCollectionsAreImmutable() {
        Map<String, List<String>> spec = new LinkedHashMap<>();
        spec.put("颜色", List.of("红", "蓝"));

        List<Map<String, String>> result = SpecCombinationGenerator.generate(spec);

        assertThrows(UnsupportedOperationException.class, () -> result.add(Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> result.get(0).put("x", "y"));
    }

    // ── 辅助 ───────────────────────────────────────────────────────
    private static List<String> buildValues(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> "V" + i)
                .toList();
    }
}
