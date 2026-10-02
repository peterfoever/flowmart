package com.flowmart.product.generator;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.enums.ProductErrorCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SpecCombinationGenerator {
    /** 本期业务上限：单个 SPU 最多 1,000 个 SKU。 */
    public static final int MAX_COMBINATIONS = 1000;

    private static final int MAX_DIMENSIONS = 3;
    private static final int MAX_VALUES_PER_DIMENSION = 20;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_VALUE_LENGTH = 64;

    private record Attribute(String name, List<String> values) { }

    private SpecCombinationGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 按 SPU 中维度和值的顺序生成笛卡尔积，最后一个维度变化最快。
     * 先校验、规范化并检查数量，再构造组合；不会修改输入。
     *
     * @param specDefinition SPU 规格列表，空列表表示无规格；null 是非法输入
     * @return 不可增删的组合列表及内层列表；每个可变组合项都是独立对象。
     *         无规格返回一个空列表组合，即 {@code [[]]}。
     * @throws BizException 规格格式错误、规范化后重名/重值，或组合超过 1000 个
     */
    public static List<List<SkuSpecValueDTO>> generate(List<SpecDTO> specDefinition) {
        if (specDefinition == null || specDefinition.size() > MAX_DIMENSIONS) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "规格列表必须提供，最多3个维度");
        }
        if (specDefinition.isEmpty()) {
            return List.of(List.of());
        }

        List<Attribute> attributes = new ArrayList<>(specDefinition.size());
        Set<String> names = new HashSet<>();
        long total = 1L;
        for (SpecDTO spec : specDefinition) {
            if (spec == null) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "规格项不能为null");
            }
            String name = normalize(spec.getName(), MAX_NAME_LENGTH, "规格名");
            if (!names.add(name)) {
                throw new BizException(ProductErrorCode.SPEC_NAME_DUPLICATE);
            }
            List<String> values = spec.getValues();
            if (values == null || values.isEmpty()) {
                throw new BizException(ProductErrorCode.SPEC_VALUES_EMPTY);
            }
            if (values.size() > MAX_VALUES_PER_DIMENSION) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "每个规格最多20个值");
            }

            List<String> copiedValues = new ArrayList<>(values.size());
            Set<String> uniqueValues = new HashSet<>();
            for (String value : values) {
                String normalized = normalize(value, MAX_VALUE_LENGTH, "规格值");
                if (!uniqueValues.add(normalized)) {
                    throw new BizException(ProductErrorCode.SPEC_VALUE_DUPLICATE);
                }
                copiedValues.add(normalized);
            }

            // 除法预检，不先计算可能溢出的乘积，也不先构造超限组合。
            if (total > MAX_COMBINATIONS / copiedValues.size()) {
                throw new BizException(ProductErrorCode.TOO_MANY_COMBINATIONS);
            }
            total *= copiedValues.size();
            attributes.add(new Attribute(name, List.copyOf(copiedValues)));
        }
        // 保留索引进位算法；顺序来自输入列表，不依赖 Map 的遍历顺序。
        int attrCount = attributes.size();
        int[] indices = new int[attrCount];
        List<List<SkuSpecValueDTO>> result = new ArrayList<>((int) total);

        while (true) {
            List<SkuSpecValueDTO> combination = new ArrayList<>(attrCount);
            for (int i = 0; i < attrCount; i++) {
                Attribute attr = attributes.get(i);
                // 列表不可变不等于元素不可变，每个组合必须新建 DTO，不能共享。
                SkuSpecValueDTO selected = new SkuSpecValueDTO();
                selected.setName(attr.name());
                selected.setValue(attr.values().get(indices[i]));
                combination.add(selected);
            }
            result.add(List.copyOf(combination));

            // 递增索引（从最后一位开始，类似计数器进位）
            int pos = attrCount - 1;
            while (pos >= 0) {
                if (indices[pos] + 1 < attributes.get(pos).values().size()) {
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
        return List.copyOf(result);
    }

    private static String normalize(String text, int maxLength, String label) {
        String normalized = StrUtil.trim(text);
        if (StrUtil.isBlank(normalized) || normalized.length() > maxLength) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    label + "不能为空且长度不能超过" + maxLength);
        }
        return normalized;
    }
}
