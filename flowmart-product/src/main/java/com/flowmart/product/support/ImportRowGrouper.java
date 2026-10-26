package com.flowmart.product.support;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.dto.imports.ImportGroup;
import com.flowmart.product.dto.imports.ImportGroupingResult;
import com.flowmart.product.dto.imports.ImportRow;
import com.flowmart.product.dto.imports.InvalidGroup;
import com.flowmart.product.dto.imports.RowError;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.imports.UngroupedRow;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpecCombinationGenerator;

import java.util.ArrayList;
import java.util.Comparator;
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

    /** 兼容原入口；无解析错误时仍执行原有组内校验。 */
    public ImportGroupingResult group(List<ImportRow> rows) {
        return group(rows, List.of());
    }

    /**
     * 两类行统一归组，任一解析错误使所属商品组整体失败。
     * 输出组和组内行均按真实行号排序；空输入由文件层决定是否拒绝。
     * @throws BizException 空行对象、非法/重复行号、错误信息缺失或规范化标识矛盾
     */
    public ImportGroupingResult group(List<ImportRow> rows, List<RowError> rowErrors) {
        List<ImportRow> parsedRows = rows == null ? List.of() : rows;
        List<RowError> errors = rowErrors == null ? List.of() : rowErrors;
        List<UngroupedRow> ungrouped = new ArrayList<>();
        Map<String, GroupBucket> byKey = new LinkedHashMap<>();
        Set<Integer> rowNumbers = new HashSet<>();

        for (ImportRow row : parsedRows) {
            if (row == null) throw invalidInput("解析行对象不能为空");
            registerRowNum(row.rowNum(), rowNumbers);
            String normalized = normalizeKeyOrNull(row.importItemKey());
            if (normalized == null) {
                ungrouped.add(new UngroupedRow(row.rowNum(), row.importItemKey(), "importItemKey",
                        ProductErrorCode.IMPORT_ITEM_KEY_INVALID.name(),
                        ProductErrorCode.IMPORT_ITEM_KEY_INVALID.getMessage()));
                continue;
            }
            GroupBucket bucket = byKey.computeIfAbsent(normalized, GroupBucket::new);
            bucket.rows.add(row);
            bucket.includePosition(row.rowNum(), row.importItemKey());
        }

        for (RowError error : errors) {
            if (error == null) throw invalidInput("行错误对象不能为空");
            registerRowNum(error.rowNum(), rowNumbers);
            if (StrUtil.isBlank(error.errorCode()) || StrUtil.isBlank(error.errorMessage())) {
                throw invalidInput("行错误必须包含错误码和说明");
            }
            // 不能信任漏填/伪造的规范化值，否则错误行可能被丢出原组，导致部分商品误导入。
            String normalized = normalizeKeyOrNull(error.importItemKey());
            if (!Objects.equals(normalized, error.importItemKeyNormalized())) {
                throw invalidInput("行错误的规范化标识与原始标识不一致，行号=" + error.rowNum());
            }
            if (normalized == null) {
                ungrouped.add(new UngroupedRow(error.rowNum(), error.importItemKey(),
                        error.field(), error.errorCode(), error.errorMessage()));
                continue;
            }
            GroupBucket bucket = byKey.computeIfAbsent(normalized, GroupBucket::new);
            bucket.parseErrors.add(error);
            bucket.includePosition(error.rowNum(), error.importItemKey());
        }

        List<ImportGroup> groups = new ArrayList<>();
        List<InvalidGroup> invalidGroups = new ArrayList<>();
        // 先收成功行再收错误行会改变 Map 插入顺序，因此显式按两类行的最早行号排序。
        var orderedBuckets = byKey.values().stream()
                .sorted(Comparator.comparingInt(bucket -> bucket.rowFrom)).toList();
        for (GroupBucket bucket : orderedBuckets) {
            bucket.rows.sort(Comparator.comparingInt(ImportRow::rowNum));
            bucket.parseErrors.sort(Comparator.comparingInt(RowError::rowNum));
            if (!bucket.parseErrors.isEmpty()) {
                invalidGroups.add(bucket.failure(ProductErrorCode.PARSE_ROW_FAILED));
                continue;
            }
            ProductErrorCode error = validateGroup(bucket.rows);
            if (error != null) {
                invalidGroups.add(bucket.failure(error));
                continue;
            }
            ImportRow first = bucket.rows.getFirst();
            groups.add(new ImportGroup(bucket.originalKey, bucket.normalizedKey,
                    StrUtil.trim(first.spuName()), first.categoryId(), first.brandId(),
                    StrUtil.trim(first.mainImage()), cleanedDefinition(first.specDefinition()), bucket.rows));
        }
        ungrouped.sort(Comparator.comparingInt(UngroupedRow::rowNum));
        return new ImportGroupingResult(groups, ungrouped, invalidGroups);
    }

    /** 只把已知的标识格式错误转为无法归组，不吞掉其他业务/系统异常。 */
    private String normalizeKeyOrNull(String key) {
        try {
            return ImportItemKeyNormalizer.normalize(key);
        } catch (BizException e) {
            if (e.getCode() != CommonErrorCode.PARAM_INVALID.getCode()) throw e;
            return null;
        }
    }

    private void registerRowNum(int rowNum, Set<Integer> seen) {
        if (rowNum < 2 || !seen.add(rowNum)) {
            throw invalidInput("真实行号须从2开始且不能重复，行号=" + rowNum);
        }
    }

    private BizException invalidInput(String message) {
        return new BizException(CommonErrorCode.PARAM_INVALID, message);
    }

    /** 仅作方法内部聚合，不访问数据库；结果 record 会复制列表，避免泄漏可变容器。 */
    private static final class GroupBucket {
        private final String normalizedKey;
        private final List<ImportRow> rows = new ArrayList<>();
        private final List<RowError> parseErrors = new ArrayList<>();
        private String originalKey;
        private int rowFrom = Integer.MAX_VALUE;
        private int rowTo = Integer.MIN_VALUE;

        private GroupBucket(String normalizedKey) {
            this.normalizedKey = normalizedKey;
        }

        private void includePosition(int rowNum, String rawKey) {
            if (rowNum < rowFrom) {
                rowFrom = rowNum;
                originalKey = rawKey;
            }
            rowTo = Math.max(rowTo, rowNum);
        }

        private InvalidGroup failure(ProductErrorCode error) {
            // 行号已跨两类列表查重，才能安全相加；范围不能用来伪造连续错误行。
            return new InvalidGroup(originalKey, normalizedKey, rowFrom, rowTo,
                    rows.size() + parseErrors.size(), error.name(), error.getMessage(), rows, parseErrors);
        }
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
