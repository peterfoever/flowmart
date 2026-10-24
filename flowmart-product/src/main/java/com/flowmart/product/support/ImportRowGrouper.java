package com.flowmart.product.support;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.dto.ImportGroup;
import com.flowmart.product.dto.ImportGroupingResult;
import com.flowmart.product.dto.ImportRow;
import com.flowmart.product.dto.InvalidGroup;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.UngroupedRow;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpecCombinationGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 事务外按规范化商品标识分组，校验公共字段与规格；不访问数据库。
 * 非法标识行进入 ungroupedRows；已知组校验失败进入 invalidGroups，不影响其他组。
 * 返回的 groups 仅表示通过本阶段校验，不代表类目、品牌、价格等已完成全部业务校验。
 */
public final class ImportRowGrouper {

    /**
     * 保留首次出现的组顺序与 Excel 原始行号，不修改输入 DTO。
     * 空输入返回空结果，文件层负责把空文件判为失败。
     * @throws BizException 行对象为空、行号小于2或重复（解析器违反输入契约）
     */
    public ImportGroupingResult group(List<ImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ImportGroupingResult(List.of(), List.of(), List.of());
        }
        List<UngroupedRow> ungrouped = new ArrayList<>();
        Map<String, List<ImportRow>> byKey = new LinkedHashMap<>();
        Set<Integer> rowNumbers = new HashSet<>();
        for (ImportRow row : rows) {
            if (row == null || row.rowNum() < 2 || !rowNumbers.add(row.rowNum())) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "解析行必须非空，真实行号须从2开始且不能重复");
            }
            String normalized;
            try {
                normalized = ImportItemKeyNormalizer.normalize(row.importItemKey());
            } catch (BizException e) {
                // 只转换已知的标识输入错误；不能吞掉未来增加的其他业务异常。
                if (e.getCode() != CommonErrorCode.PARAM_INVALID.getCode()) throw e;
                ungrouped.add(new UngroupedRow(row.rowNum(), row.importItemKey(),
                        ProductErrorCode.IMPORT_ITEM_KEY_INVALID.getMessage()));
                continue;
            }
            byKey.computeIfAbsent(normalized, k -> new ArrayList<>()).add(row);
        }

        List<ImportGroup> groups = new ArrayList<>();
        List<InvalidGroup> invalidGroups = new ArrayList<>();
        for (var entry : byKey.entrySet()) {
            List<ImportRow> groupRows = entry.getValue();
            ImportRow first = groupRows.getFirst();
            ProductErrorCode error = validateGroup(groupRows);
            if (error == null) {
                groups.add(new ImportGroup(first.importItemKey(), entry.getKey(),
                        StrUtil.trim(first.spuName()), first.categoryId(), first.brandId(),
                        StrUtil.trim(first.mainImage()), cleanedDefinition(first.specDefinition()), groupRows));
            } else {
                // 非连续行不能用 rowFrom..rowTo 生成错误行；写错误时必须遍历 rows。
                int rowFrom = groupRows.stream().mapToInt(ImportRow::rowNum).min().orElseThrow();
                int rowTo = groupRows.stream().mapToInt(ImportRow::rowNum).max().orElseThrow();
                invalidGroups.add(new InvalidGroup(first.importItemKey(), entry.getKey(),
                        rowFrom, rowTo, groupRows.size(), error.name(), error.getMessage(), groupRows));
            }
        }
        return new ImportGroupingResult(groups, ungrouped, invalidGroups);
    }

    /** 成功返回 null，失败返回明确枚举，不进行字符串和整数错误码的反查。 */
    private ProductErrorCode validateGroup(List<ImportRow> rows) {
        ImportRow first = rows.getFirst();
        Set<String> allowedHashes;
        try {
            Map<String, Set<String>> firstDefinition = normalizeSpecDefinition(first.specDefinition());
            // 复用既有边界校验（维度、值数量、长度、组合上限），每组只展开一次。
            allowedHashes = SpecCombinationGenerator.generate(first.specDefinition()).stream()
                    .map(SpecHashCalculator::calculate).collect(Collectors.toSet());
            for (int i = 1; i < rows.size(); i++) {
                ImportRow row = rows.get(i);
                Map<String, Set<String>> definition = normalizeSpecDefinition(row.specDefinition());
                if (!Objects.equals(StrUtil.trim(first.spuName()), StrUtil.trim(row.spuName()))
                        || !Objects.equals(first.categoryId(), row.categoryId())
                        || !Objects.equals(first.brandId(), row.brandId())
                        || !Objects.equals(StrUtil.trim(first.mainImage()), StrUtil.trim(row.mainImage()))
                        || !firstDefinition.equals(definition)) {
                    return ProductErrorCode.GROUP_FIELD_INCONSISTENT;
                }
            }
        } catch (BizException e) {
            // 只处理规格校验器明确约定的异常，不把未知系统/业务错误伪装成组失败。
            if (e.getCode() == CommonErrorCode.PARAM_INVALID.getCode()) {
                return ProductErrorCode.GROUP_SPEC_DEFINITION_INVALID;
            }
            ProductErrorCode error = ProductErrorCode.fromCode(e.getCode());
            if (error == ProductErrorCode.GROUP_SPEC_DEFINITION_INVALID
                    || error == ProductErrorCode.SPEC_NAME_DUPLICATE
                    || error == ProductErrorCode.SPEC_VALUE_DUPLICATE
                    || error == ProductErrorCode.SPEC_VALUES_EMPTY
                    || error == ProductErrorCode.TOO_MANY_COMBINATIONS) {
                return error;
            }
            throw e;
        }
        if (first.specDefinition().isEmpty() && rows.size() != 1) {
            return ProductErrorCode.GROUP_NO_SPEC_MULTIPLE_ROWS;
        }
        if (rows.size() > SpecCombinationGenerator.MAX_COMBINATIONS) {
            return ProductErrorCode.TOO_MANY_COMBINATIONS;
        }
        Set<String> seenHashes = new HashSet<>();
        for (ImportRow row : rows) {
            String hash;
            try {
                hash = SpecHashCalculator.calculate(row.specCombination());
            } catch (BizException e) {
                if (e.getCode() != CommonErrorCode.PARAM_INVALID.getCode()
                        && e.getCode() != ProductErrorCode.SPEC_NAME_DUPLICATE.getCode()) throw e;
                return ProductErrorCode.SKU_SPEC_MISMATCH;
            }
            if (!allowedHashes.contains(hash)) return ProductErrorCode.SKU_SPEC_MISMATCH;
            if (!seenHashes.add(hash)) return ProductErrorCode.GROUP_SPEC_COMBINATION_DUPLICATE;
        }
        return null;
    }

    /** 校验后再集合化比较；禁止用覆盖、去重或 continue 掩盖非法规格。 */
    private Map<String, Set<String>> normalizeSpecDefinition(List<SpecDTO> specs) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (SpecDTO spec : specs) {
            if (spec == null || StrUtil.isBlank(StrUtil.trim(spec.getName()))) {
                throw new BizException(ProductErrorCode.GROUP_SPEC_DEFINITION_INVALID);
            }
            String name = StrUtil.trim(spec.getName());
            if (result.containsKey(name)) throw new BizException(ProductErrorCode.SPEC_NAME_DUPLICATE);
            if (spec.getValues() == null || spec.getValues().isEmpty()) {
                throw new BizException(ProductErrorCode.SPEC_VALUES_EMPTY);
            }
            Set<String> values = new LinkedHashSet<>();
            for (String rawValue : spec.getValues()) {
                String value = StrUtil.trim(rawValue);
                if (StrUtil.isBlank(value)) throw new BizException(ProductErrorCode.GROUP_SPEC_DEFINITION_INVALID);
                if (!values.add(value)) throw new BizException(ProductErrorCode.SPEC_VALUE_DUPLICATE);
            }
            result.put(name, values);
        }
        return result;
    }

    /** 只在校验通过后生成干净副本，保留首行规格展示顺序，不修改原始行。 */
    private List<SpecDTO> cleanedDefinition(List<SpecDTO> source) {
        List<SpecDTO> cleaned = new ArrayList<>();
        for (SpecDTO spec : source) {
            SpecDTO copy = new SpecDTO();
            copy.setName(StrUtil.trim(spec.getName()));
            copy.setValues(spec.getValues().stream().map(StrUtil::trim).toList());
            cleaned.add(copy);
        }
        return cleaned;
    }
}
