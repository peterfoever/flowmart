package com.flowmart.product.generator;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.enums.ProductErrorCode;

import java.util.*;

public final class SpecCombinationGenerator {
    /** 本期业务上限：单个 SPU 最多 1,000 个 SKU。 */
    public static final int MAX_COMBINATIONS = 1000;

    private SpecCombinationGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 生成所有规格组合。
     * @param specDefinition
     * @return
     */
    public static List<Map<String, String>> generate(Map<String, List<String>> specDefinition){
        // ── 规则 1：无规格 → 一个空组合 ──────────────────────────────
        if (specDefinition == null || specDefinition.isEmpty()) {
            return Collections.singletonList(Collections.emptyMap());
        }

        // ── 规则 4：校验规格名与规格值列表，同时预计算组合总数 ─────────
        List<Map.Entry<String, List<String>>> attributes = new ArrayList<>(specDefinition.size());
        long total = 1L;
        for (Map.Entry<String, List<String>> entry : specDefinition.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if(values == null || values.isEmpty()) {
                throw new BizException(ProductErrorCode.SPEC_VALUES_EMPTY);
            }

            // 规则 5：拷贝一份，避免后续被外部修改影响
            List<String> copiedValues = List.copyOf(values);

            // 规则 3：提前检测溢出，超过 1,000 立即拒绝
            if(total>MAX_COMBINATIONS / copiedValues.size()){
                throw new BizException(ProductErrorCode.TOO_MANY_COMBINATIONS);
            }
            total *= copiedValues.size();
            attributes.add(new AbstractMap.SimpleImmutableEntry<>(name, copiedValues));
        }
        // ── 规则 2：笛卡尔积展开（迭代法，避免递归栈溢出） ─────────────
        int attrCount = attributes.size();
        int[] indices = new int[attrCount];
        List<Map<String, String>> result = new ArrayList<>((int) total);

        while (true) {
            // 构造当前组合：每次 new 一个 Map，保证各组合之间不共享
            Map<String, String> combination = new LinkedHashMap<>(attrCount);
            for (int i = 0; i < attrCount; i++) {
                Map.Entry<String, List<String>> attr = attributes.get(i);
                combination.put(attr.getKey(), attr.getValue().get(indices[i]));
            }
            result.add(Collections.unmodifiableMap(combination));

            // 递增索引（从最后一位开始，类似计数器进位）
            int pos = attrCount - 1;
            while (pos >= 0) {
                if (indices[pos] + 1 < attributes.get(pos).getValue().size()) {
                    indices[pos]++;
                    break;
                }
                indices[pos] = 0;
                pos--;
            }
            if (pos < 0) {
                break; // 所有位都回绕，展开完成
            }
        }
        return Collections.unmodifiableList(result);
    }
}
