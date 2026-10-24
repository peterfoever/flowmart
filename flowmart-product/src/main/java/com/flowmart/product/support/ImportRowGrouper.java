package com.flowmart.product.support;

import cn.hutool.core.util.StrUtil;
import com.flowmart.product.dto.*;
import com.flowmart.product.enums.ProductErrorCode;

import java.util.*;

/**
 * 把 Excel 行按规范化标识分组，并做组内一致性校验。
 * <p>
 * 规则：
 * 1. 标识缺失或非法 → 进入 ungroupedRows，不创建分组。
 * 2. 合法标识 → 按规范化标识分组，保持首次出现顺序。
 * 3. 组内校验失败 → 进入 invalidGroups，整组不进入 groups。
 * 4. 组内校验通过 → 进入 groups。
 * <p>
 * 本类不访问数据库，不做类目、品牌存在性校验，也不写商品表。
 */
public final class ImportRowGrouper {

    public ImportGroupingResult group(List<ImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ImportGroupingResult(List.of(), List.of(), List.of());
        }

        List<UngroupedRow> ungrouped = new ArrayList<>();
        Map<String, List<ImportRow>> byKey = new LinkedHashMap<>();

        // 1. 规范化标识并分组
        for (ImportRow row : rows) {
            String normalized;
            try {
                normalized = ImportItemKeyNormalizer.normalize(row.importItemKey());
            } catch (IllegalArgumentException e) {
                ungrouped.add(new UngroupedRow(
                        row.rowNum(),
                        row.importItemKey(),
                        ProductErrorCode.IMPORT_ITEM_KEY_INVALID.getMessage()
                ));
                continue;
            }

            if (normalized.isEmpty()) {
                ungrouped.add(new UngroupedRow(
                        row.rowNum(),
                        row.importItemKey(),
                        ProductErrorCode.IMPORT_ITEM_KEY_INVALID.getMessage()
                ));
                continue;
            }
            byKey.computeIfAbsent(normalized, k -> new ArrayList<>()).add(row);
        }

        // 2. 逐组校验
        List<ImportGroup> groups = new ArrayList<>();
        List<InvalidGroup> invalidGroups = new ArrayList<>();

        for (Map.Entry<String, List<ImportRow>> entry : byKey.entrySet()) {
            String normalizedKey = entry.getKey();
            List<ImportRow> groupRows = entry.getValue();
            String originalKey = groupRows.get(0).importItemKey();

            String errorCode = validateGroup(groupRows);
            if (errorCode == null) {
                ImportRow first = groupRows.get(0);
                groups.add(new ImportGroup(
                        originalKey,
                        normalizedKey,
                        StrUtil.trim(first.spuName()),
                        first.categoryId(),
                        first.brandId(),
                        StrUtil.trim(first.mainImage()),
                        first.specDefinition(),
                        groupRows
                ));
            } else {
                int rowFrom = groupRows.stream().mapToInt(ImportRow::rowNum).min().orElse(0);
                int rowTo = groupRows.stream().mapToInt(ImportRow::rowNum).max().orElse(0);
                invalidGroups.add(new InvalidGroup(
                        originalKey,
                        normalizedKey,
                        rowFrom,
                        rowTo,
                        groupRows.size(),
                        errorCode,
                        describe(errorCode),
                        groupRows
                ));
            }
        }
        return new ImportGroupingResult(groups, ungrouped, invalidGroups);
    }


    private String describe(String errorCode) {
        for (ProductErrorCode e : ProductErrorCode.values()) {
            if (Objects.equals(e.getCode(),errorCode); return e.getMessage();
        }
        return errorCode;
    }

    /**
     * 返回 null 表示校验通过；否则返回错误码。
     */
    private String validateGroup(List<ImportRow> rows) {
        ImportRow first = rows.get(0);
        // 字段一致性
        Map<String, Set<String>> firstSpecDef = normalizeSpecDefinition(first.specDefinition());
        for (int i = 1; i < rows.size(); i++) {
            ImportRow row = rows.get(i);
            if (!Objects.equals(StrUtil.trim(first.spuName()), StrUtil.trim(row.spuName()))
                    || !Objects.equals(first.categoryId(), row.categoryId())
                    || !Objects.equals(first.brandId(), row.brandId())
                    || !Objects.equals(StrUtil.trim(first.mainImage()), StrUtil.trim(row.mainImage()))
                    || !normalizeSpecDefinition(row.specDefinition()).equals(firstSpecDef)) {
                return ProductErrorCode.GROUP_FIELD_INCONSISTENT;
            }
        }
    }

    private Map<String, Set<String>> normalizeSpecDefinition(List<SkuSpecValueDTO> list) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new TreeMap<>();
        for (SkuSpecValueDTO dto : list) {
            String name = StrUtil.trim(dto.getName());
            String value = StrUtil.trim(dto.getValue());
            if (StrUtil.isBlank(name) || StrUtil.isBlank(value)) {
                continue;
            }
            result.computeIfAbsent(name, k -> new TreeSet<>()).add(value);
        }
        return result;
    }
}
