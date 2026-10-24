package com.flowmart.product.support;





import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.imports.ImportParseResult;
import com.flowmart.product.dto.imports.ImportRow;
import com.flowmart.product.dto.imports.RowError;
import com.flowmart.product.enums.ProductErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 把原始单元格转换成 ImportRow 或 RowError。
 *
 * 不访问数据库，不判断类目、品牌是否存在，也不判断同组字段是否一致。
 * 每行只保留一个代表错误，检查顺序固定：
 * 标识 → 名称 → 类目 → 品牌 → 主图 → 规格定义 → 规格组合 → 价格 → SKU 图片
 */
public final class ImportRowValidator {

    private static final Pattern SNOWFLAKE_ID = Pattern.compile("^[1-9][0-9]{0,18}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999999999.99");
    private static final int MAX_SPU_NAME = 128;
    private static final int MAX_IMAGE_URL = 512;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(50).build())
            .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ImportParseResult validate(List<ImportRow> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return ImportParseResult.empty();
        }

        List<ImportRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        for (ImportRow raw : rawRows) {
            if (raw == null) continue;
            if (raw.allBlank()) continue;
            validateOne(raw, rows, errors);
        }

        return new ImportParseResult(rows, errors, null);
    }

    private void validateOne(ImportRow raw, List<ImportRow> rows, List<RowError> errors) {

        // 1. 标识先处理，保存规范化值
        String rawKey = raw.cell(ImportColumns.IMPORT_ITEM_KEY).rawValue();
        String normalizedKey = null;
        String keyError = null;
        if (StrUtil.isNotBlank(rawKey)) {
            try {
                normalizedKey = ImportItemKeyNormalizer.normalize(rawKey);
            } catch (RuntimeException e) {
                keyError = "导入商品标识缺失或不合法";
            }
        } else {
            keyError = "导入商品标识缺失";
        }
        if (keyError != null) {
            errors.add(new RowError(raw.rowNum(), rawKey, null,
                    "importItemKey", ProductErrorCode.IMPORT_ITEM_KEY_INVALID.name(), keyError));
            return;
        }

        // 2. 名称
        String spuName = readRequiredText(raw, ImportColumns.SPU_NAME, rawKey, normalizedKey, errors,
                "spuName", ProductErrorCode.SPU_NAME_REQUIRED.name(), MAX_SPU_NAME);
        if (spuName == null) return;

        // 3. 类目 ID
        Long categoryId = readRequiredId(raw, ImportColumns.CATEGORY_ID, rawKey, normalizedKey, errors,
                "categoryId", ProductErrorCode.CATEGORY_REQUIRED.name());
        if (categoryId == null) return;

        // 4. 品牌 ID（可空）
        Long brandId = readOptionalId(raw, ImportColumns.BRAND_ID, rawKey, normalizedKey, errors, "brandId");
        if (hasError(errors, raw.rowNum())) return;

        // 5. 主图
        String mainImage = readRequiredText(raw, ImportColumns.MAIN_IMAGE, rawKey, normalizedKey, errors,
                "mainImage", ProductErrorCode.MAIN_IMAGE_REQUIRED.name(), MAX_IMAGE_URL);
        if (mainImage == null) return;

        // 6. 规格定义
        List<SpecDTO> specDefinition = readSpecDefinition(raw, rawKey, normalizedKey, errors);
        if (hasError(errors, raw.rowNum())) return;

        // 7. 规格组合
        List<SkuSpecValueDTO> specCombination = readSpecCombination(raw, rawKey, normalizedKey, errors);
        if (hasError(errors, raw.rowNum())) return;

        // 8. 价格
        BigDecimal price = readPrice(raw, rawKey, normalizedKey, errors);
        if (price == null) return;

        // 9. SKU 图片
        String skuImage = readRequiredText(raw, ImportColumns.SKU_IMAGE, rawKey, normalizedKey, errors,
                "skuImage", ProductErrorCode.SKU_IMAGE_REQUIRED.name(), MAX_IMAGE_URL);
        if (skuImage == null) return;

        rows.add(new ImportRow(raw.rowNum(), rawKey, spuName, categoryId, brandId, mainImage,
                specDefinition, specCombination, price, skuImage));
    }

    // ── 文本字段 ───────────────────────────────────────────────────

    private String readRequiredText(RawImportRow raw, int column, String rawKey, String normalizedKey,
                                    List<RowError> errors, String field, String requiredCode, int maxLength) {
        RawCell cell = raw.cell(column);
        rejectFormulaOrError(raw, cell, rawKey, normalizedKey, errors, field);
        if (hasError(errors, raw.rowNum())) return null;
        String value = cell.rawValue();
        if (StrUtil.isBlank(value)) {
            errors.add(error(raw, rawKey, normalizedKey, field, requiredCode, "字段不能为空"));
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.TEXT_TOO_LONG.name(), "字段长度不能超过 " + maxLength + " 字符"));
            return null;
        }
        return trimmed;
    }

    // ── ID 字段 ────────────────────────────────────────────────────

    private Long readRequiredId(RawImportRow raw, int column, String rawKey, String normalizedKey,
                                List<RowError> errors, String field, String requiredCode) {
        Long id = readId(raw, column, rawKey, normalizedKey, errors, field, false);
        if (hasError(errors, raw.rowNum())) return null;
        if (id == null) {
            errors.add(error(raw, rawKey, normalizedKey, field, requiredCode, "字段不能为空"));
            return null;
        }
        return id;
    }

    private Long readOptionalId(RawImportRow raw, int column, String rawKey, String normalizedKey,
                                List<RowError> errors, String field) {
        return readId(raw, column, rawKey, normalizedKey, errors, field, true);
    }

    private Long readId(RawImportRow raw, int column, String rawKey, String normalizedKey,
                        List<RowError> errors, String field, boolean optional) {
        RawCell cell = raw.cell(column);
        if (cell.type() == RawCellType.NUMBER) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.ID_NOT_TEXT.name(),
                    "ID 必须为文本格式的雪花 ID，请将单元格设置为文本"));
            return null;
        }
        rejectFormulaOrError(raw, cell, rawKey, normalizedKey, errors, field);
        if (hasError(errors, raw.rowNum())) return null;
        String value = cell.rawValue();
        if (StrUtil.isBlank(value)) {
            if (optional) return null;
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.ID_REQUIRED.name(), "字段不能为空"));
            return null;
        }
        String trimmed = value.trim();
        if (!SNOWFLAKE_ID.matcher(trimmed).matches()) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.ID_INVALID.name(), "ID 必须为 1~19 位正整数"));
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.ID_INVALID.name(), "ID 超出 Long 范围"));
            return null;
        }
    }

    // ── 规格 ──────────────────────────────────────────────────────

    private List<SpecDTO> readSpecDefinition(RawImportRow raw, String rawKey, String normalizedKey,
                                             List<RowError> errors) {
        RawCell cell = raw.cell(ImportColumns.SPEC_DEFINITION);
        rejectFormulaOrError(raw, cell, rawKey, normalizedKey, errors, "specDefinition");
        if (hasError(errors, raw.rowNum())) return List.of();
        String value = cell.rawValue();
        if (StrUtil.isBlank(value) || "[]".equals(value.trim())) {
            return List.of();
        }
        try {
            List<SpecDTO> list = JSON.readValue(value,
                    JSON.getTypeFactory().constructCollectionType(List.class, SpecDTO.class));
            return list == null ? List.of() : list;
        } catch (Exception e) {
            errors.add(error(raw, rawKey, normalizedKey, "specDefinition",
                    ProductErrorCode.SPEC_DEFINITION_INVALID.name(),
                    "规格定义必须是合法的 JSON 数组"));
            return List.of();
        }
    }

    private List<SkuSpecValueDTO> readSpecCombination(RawImportRow raw, String rawKey, String normalizedKey,
                                                      List<RowError> errors) {
        RawCell cell = raw.cell(ImportColumns.SPEC_COMBINATION);
        rejectFormulaOrError(raw, cell, rawKey, normalizedKey, errors, "specCombination");
        if (hasError(errors, raw.rowNum())) return List.of();
        String value = cell.rawValue();
        if (StrUtil.isBlank(value) || "[]".equals(value.trim())) {
            return List.of();
        }
        try {
            List<SkuSpecValueDTO> list = JSON.readValue(value,
                    JSON.getTypeFactory().constructCollectionType(List.class, SkuSpecValueDTO.class));
            return list == null ? List.of() : list;
        } catch (Exception e) {
            errors.add(error(raw, rawKey, normalizedKey, "specCombination",
                    ProductErrorCode.SPEC_COMBINATION_INVALID.name(),
                    "规格组合必须是合法的 JSON 数组"));
            return List.of();
        }
    }

    // ── 价格 ──────────────────────────────────────────────────────

    private BigDecimal readPrice(RawImportRow raw, String rawKey, String normalizedKey, List<RowError> errors) {
        RawCell cell = raw.cell(ImportColumns.PRICE);
        rejectFormulaOrError(raw, cell, rawKey, normalizedKey, errors, "price");
        if (hasError(errors, raw.rowNum())) return null;
        String value = cell.rawValue();
        if (StrUtil.isBlank(value)) {
            errors.add(error(raw, rawKey, normalizedKey, "price",
                    ProductErrorCode.SKU_PRICE_REQUIRED.name(), "销售价不能为空"));
            return null;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            errors.add(error(raw, rawKey, normalizedKey, "price",
                    ProductErrorCode.SKU_PRICE_INVALID.name(), "销售价必须为合法数字"));
            return null;
        }
        if (price.signum() < 0) {
            errors.add(error(raw, rawKey, normalizedKey, "price",
                    ProductErrorCode.SKU_PRICE_NEGATIVE.name(), "销售价不能为负数"));
            return null;
        }
        if (price.scale() > 2) {
            errors.add(error(raw, rawKey, normalizedKey, "price",
                    ProductErrorCode.SKU_PRICE_SCALE_EXCEEDED.name(), "销售价最多两位小数"));
            return null;
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            errors.add(error(raw, rawKey, normalizedKey, "price",
                    ProductErrorCode.SKU_PRICE_OVER_LIMIT.name(), "销售价超过上限"));
            return null;
        }
        return price;
    }

    // ── 公共辅助 ──────────────────────────────────────────────────

    private void rejectFormulaOrError(RawImportRow raw, RawCell cell, String rawKey, String normalizedKey,
                                      List<RowError> errors, String field) {
        if (cell.type() == RawCellType.FORMULA) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.CELL_FORMULA_NOT_ALLOWED.name(),
                    "公式单元格不允许，请粘贴为值"));
        } else if (cell.type() == RawCellType.ERROR) {
            errors.add(error(raw, rawKey, normalizedKey, field,
                    ProductErrorCode.CELL_ERROR_VALUE.name(),
                    "单元格为错误值，请检查公式或数据来源"));
        }
    }

    private static boolean hasError(List<RowError> errors, int rowNum) {
        return errors.stream().anyMatch(e -> e.rowNum() == rowNum);
    }

    private static RowError error(RawImportRow raw, String rawKey, String normalizedKey,
                                  String field, String code, String message) {
        return new RowError(raw.rowNum(), rawKey, normalizedKey, field, code, message);
    }
}
