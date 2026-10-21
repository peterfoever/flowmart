package com.flowmart.product.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImportGroupStatus {
    SUCCEEDED("SUCCEEDED", "该组导入成功"),
    FAILED("FAILED", "该组导入失败"),
    ;

    private final String code;
    private final String description;

    public static ImportGroupStatus of(String code) {
        for (ImportGroupStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知分组状态: " + code);
    }
}
