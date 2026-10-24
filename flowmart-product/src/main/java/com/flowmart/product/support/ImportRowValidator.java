package com.flowmart.product.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.imports.ImportParseResult;
import com.flowmart.product.dto.imports.ImportRow;
import com.flowmart.product.dto.imports.RawCell;
import com.flowmart.product.dto.imports.RawImportRow;
import com.flowmart.product.dto.imports.RowError;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.enums.RawCellType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 事务外把原始单元格转换成业务行或一个代表错误，不访问数据库。
 * 顺序固定：标识→名称→类目→品牌→主图→定义→组合→价格→SKU图片。
 * 规格重复、组内一致性及组合与定义匹配由ImportRowGrouper负责。
 */
public final class ImportRowValidator {
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    // 与现有SKU服务及DECIMAL(10,2)保持一致，不允许通过舍入修复输入。
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_SPEC_JSON_LENGTH = 4096;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(50).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * 全空行跳过但保留后续真实行号；null列表视为空输入。
     * @throws BizException 空行对象、非法/重复行号、超出模板列范围（程序输入契约错误）
     */
    public ImportParseResult validate(List<RawImportRow> rawRows) {
        List<ImportRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<Integer> seenRows = new HashSet<>();
        if (rawRows != null) {
            for (RawImportRow raw : rawRows) {
                if (raw == null || raw.rowNum() < 2 || !seenRows.add(raw.rowNum())) {
                    throw new BizException(CommonErrorCode.PARAM_INVALID, "原始行须非空，真实行号从2开始且不能重复");
                }
                if (raw.cells().keySet().stream().anyMatch(column -> column >= ImportColumns.COLUMN_COUNT)) {
                    throw new BizException(CommonErrorCode.PARAM_INVALID, "原始行包含模板以外的列");
                }
                if (!raw.allBlank()) validateOne(raw, rows, errors);
            }
        }
        return new ImportParseResult(rows, errors, null);
    }

    private void validateOne(RawImportRow raw, List<ImportRow> rows, List<RowError> errors) {
        RawCell keyCell = raw.cell(ImportColumns.IMPORT_ITEM_KEY);
        // 非文本标识不是可信的业务标识：不把数字/公式缓存值交给分组器建立商品归属。
        String rawKey = keyCell.type() == RawCellType.TEXT ? keyCell.rawValue() : null;
        String normalizedKey = null;
        try {
            checkCell(keyCell, "importItemKey", false);
            try {
                normalizedKey = ImportItemKeyNormalizer.normalize(rawKey);
            } catch (BizException e) {
                if (e.getCode() != CommonErrorCode.PARAM_INVALID.getCode()) throw e;
                throw fail("importItemKey", ProductErrorCode.IMPORT_ITEM_KEY_INVALID, e.getMessage());
            }
            String name = requiredText(raw, ImportColumns.SPU_NAME, "spuName", MAX_NAME_LENGTH,
                    ProductErrorCode.IMPORT_FIELD_REQUIRED);
            Long categoryId = readId(raw, ImportColumns.CATEGORY_ID, "categoryId", false);
            Long brandId = readId(raw, ImportColumns.BRAND_ID, "brandId", true);
            String mainImage = requiredText(raw, ImportColumns.MAIN_IMAGE, "mainImage", MAX_URL_LENGTH,
                    ProductErrorCode.IMPORT_FIELD_REQUIRED);
            List<SpecDTO> definition = readDefinition(raw);
            List<SkuSpecValueDTO> combination = readCombination(raw);
            BigDecimal price = readPrice(raw);
            String skuImage = requiredText(raw, ImportColumns.SKU_IMAGE, "skuImage", MAX_URL_LENGTH,
                    ProductErrorCode.SKU_IMAGE_REQUIRED);
            rows.add(new ImportRow(raw.rowNum(), rawKey, name, categoryId, brandId,
                    mainImage, definition, combination, price, skuImage));
        } catch (CellValidationException e) {
            // 只捕获本类明确产生的字段错误，系统故障必须上抛，不能伪装成某行填错。
            errors.add(new RowError(raw.rowNum(), rawKey, normalizedKey,
                    e.field, e.code.name(), e.getMessage()));
        }
    }

    private String requiredText(RawImportRow raw, int column, String field, int maxLength,
                                ProductErrorCode requiredCode) {
        RawCell cell = raw.cell(column);
        checkCell(cell, field, false);
        String value = StrUtil.trim(cell.rawValue());
        if (StrUtil.isBlank(value)) throw fail(field, requiredCode, "字段不能为空");
        if (value.length() > maxLength) {
            throw fail(field, ProductErrorCode.IMPORT_TEXT_TOO_LONG, "字段长度不能超过" + maxLength);
        }
        return value;
    }

    private Long readId(RawImportRow raw, int column, String field, boolean optional) {
        RawCell cell = raw.cell(column);
        if (cell.type() == RawCellType.NUMBER) {
            throw fail(field, ProductErrorCode.IMPORT_ID_NOT_TEXT, "ID必须来自文本单元格，请按原始ID重新填写");
        }
        checkCell(cell, field, false);
        String value = StrUtil.trim(cell.rawValue());
        if (StrUtil.isBlank(value)) {
            if (optional) return null;
            throw fail(field, ProductErrorCode.IMPORT_FIELD_REQUIRED, "ID不能为空");
        }
        if (!POSITIVE_ID.matcher(value).matches()) {
            throw fail(field, ProductErrorCode.IMPORT_ID_INVALID, "ID必须为1～19位正整数");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw fail(field, ProductErrorCode.IMPORT_ID_INVALID, "ID超出Long范围");
        }
    }

    private List<SpecDTO> readDefinition(RawImportRow raw) {
        String field = "specDefinition";
        ProductErrorCode code = ProductErrorCode.SPEC_DEFINITION_INVALID;
        JsonNode root = readSpecArray(raw.cell(ImportColumns.SPEC_DEFINITION), field, code);
        List<SpecDTO> result = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject() || item.size() != 2 || !item.path("name").isTextual()
                    || !item.path("values").isArray()) throw fail(field, code, code.getMessage());
            List<String> values = new ArrayList<>();
            for (JsonNode value : item.get("values")) {
                if (!value.isTextual()) throw fail(field, code, "规格值必须为字符串，不能自动转换数字或布尔值");
                values.add(value.textValue());
            }
            SpecDTO spec = new SpecDTO();
            spec.setName(item.get("name").textValue());
            spec.setValues(List.copyOf(values));
            result.add(spec);
        }
        return result;
    }

    private List<SkuSpecValueDTO> readCombination(RawImportRow raw) {
        String field = "specCombination";
        ProductErrorCode code = ProductErrorCode.SPEC_COMBINATION_INVALID;
        JsonNode root = readSpecArray(raw.cell(ImportColumns.SPEC_COMBINATION), field, code);
        List<SkuSpecValueDTO> result = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject() || item.size() != 2 || !item.path("name").isTextual()
                    || !item.path("value").isTextual()) throw fail(field, code, code.getMessage());
            SkuSpecValueDTO spec = new SkuSpecValueDTO();
            spec.setName(item.get("name").textValue());
            spec.setValue(item.get("value").textValue());
            result.add(spec);
        }
        return result;
    }

    /** 显式校验JSON树类型，不依赖Jackson把数字/布尔值强制转换成String。 */
    private JsonNode readSpecArray(RawCell cell, String field, ProductErrorCode code) {
        checkCell(cell, field, false);
        String value = StrUtil.trim(cell.rawValue());
        if (StrUtil.isBlank(value)) return JSON.createArrayNode();
        if (value.length() > MAX_SPEC_JSON_LENGTH) throw fail(field, code, "规格JSON不能超过4096个字符");
        try {
            JsonNode node = JSON.readTree(value);
            if (node == null || !node.isArray()) throw fail(field, code, "必须为JSON数组，不能为null或对象");
            return node;
        } catch (JsonProcessingException e) {
            throw fail(field, code, "JSON格式非法，不能含重复键、尾随内容或过深嵌套");
        }
    }

    private BigDecimal readPrice(RawImportRow raw) {
        RawCell cell = raw.cell(ImportColumns.PRICE);
        checkCell(cell, "price", true);
        String value = StrUtil.trim(cell.rawValue());
        if (StrUtil.isBlank(value)) throw fail("price", ProductErrorCode.SKU_PRICE_INVALID, "销售价不能为空");
        BigDecimal price;
        try {
            price = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw fail("price", ProductErrorCode.SKU_PRICE_INVALID, "销售价必须为合法数字");
        }
        if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2) {
            throw fail("price", ProductErrorCode.SKU_PRICE_INVALID, "销售价须在0～99999999.99之间且最多两位小数");
        }
        return price;
    }

    private void checkCell(RawCell cell, String field, boolean allowNumber) {
        if (cell.type() == RawCellType.FORMULA) {
            throw fail(field, ProductErrorCode.CELL_FORMULA_NOT_ALLOWED, "公式单元格不允许，请粘贴为值");
        }
        if (cell.type() == RawCellType.ERROR) {
            throw fail(field, ProductErrorCode.CELL_ERROR_VALUE, "单元格为错误值，请检查原始文件");
        }
        if (!allowNumber && cell.type() == RawCellType.NUMBER) {
            throw fail(field, ProductErrorCode.IMPORT_CELL_TYPE_INVALID, "该字段必须为文本单元格");
        }
    }

    private CellValidationException fail(String field, ProductErrorCode code, String message) {
        return new CellValidationException(field, code, message);
    }

    /** 方法内部控制一行快速失败，不跨出Validator，不代替项目统一BizException。 */
    private static final class CellValidationException extends RuntimeException {
        private final String field;
        private final ProductErrorCode code;

        private CellValidationException(String field, ProductErrorCode code, String message) {
            super(message, null, false, false);
            this.field = field;
            this.code = code;
        }
    }
}
