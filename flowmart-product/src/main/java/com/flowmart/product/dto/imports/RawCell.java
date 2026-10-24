package com.flowmart.product.dto.imports;

import cn.hutool.core.util.StrUtil;
import com.flowmart.product.enums.RawCellType;

/**
 * 原始单元格。
 * rawValue 是该单元格的原始文本；BLANK 时为 null。
 * 不能用 String 替代，因为需要区分文本格式和数字格式的 ID，也要识别公式。
 */
public record RawCell(RawCellType type, String rawValue) {

    public RawCell {
        if (type == null) throw new IllegalArgumentException("单元格类型不能为空");
        if (type == RawCellType.BLANK && StrUtil.isNotBlank(rawValue)) {
            throw new IllegalArgumentException("BLANK单元格不能携带非空内容");
        }
    }

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
        return switch (type) {
            case BLANK -> true;
            case TEXT -> StrUtil.isBlank(rawValue);
            case NUMBER, FORMULA, ERROR -> false;
        };
    }
}
