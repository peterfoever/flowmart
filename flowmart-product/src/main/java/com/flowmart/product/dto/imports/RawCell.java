package com.flowmart.product.dto.imports;

import com.flowmart.product.enums.RawCellType;

/**
 * 原始单元格。
 * rawValue 是该单元格的原始文本；BLANK 时为 null。
 * 不能用 String 替代，因为需要区分文本格式和数字格式的 ID，也要识别公式。
 */
public record RawCell(RawCellType type, String rawValue) {

    public static RawCell blank() {
        return new RawCell(RawCellType.BLANK, null);
    }

    public static RawCell text(String value) {
        return new RawCell(RawCellType.TEXT, value);
    }

    public static RawCell number(String rawValue) {
        return new RawCell(RawCellType.NUMBER, rawValue);
    }

    public static RawCell formula() {
        return new RawCell(RawCellType.FORMULA, null);
    }

    public static RawCell error() {
        return new RawCell(RawCellType.ERROR, null);
    }

    public boolean isBlank() {
        return type == RawCellType.BLANK || (rawValue == null && type != RawCellType.ERROR);
    }
}
