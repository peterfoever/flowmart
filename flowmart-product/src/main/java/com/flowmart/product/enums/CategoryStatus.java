package com.flowmart.product.enums;


import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

@Getter
public enum CategoryStatus {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final Integer code;
    private final String desc;

    CategoryStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 状态码
     * @return 枚举，找不到返回 null
     */
    public static CategoryStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CategoryStatus status : values()) {
            if (Objects.equals(status.code, code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断某个 code 是否等于当前枚举
     *
     * @param code 状态码
     * @return true 如果 code 等于当前枚举的 code
     */
    public boolean matches(Integer code) {
        return Objects.equals(code, this.code);
    }

    /**
     * 判断前端传来的 0/1 是否合法
     *
     * @param code 状态码
     * @return true 如果 code 是合法的枚举值
     */
    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(status -> status.matches(code));
    }

    /**
     * 根据 code 获取描述，找不到返回 null
     *
     * @param code 状态码
     * @return 描述，找不到返回 null
     */
    public static String getDescByCode(Integer code) {
        CategoryStatus status = fromCode(code);
        return status == null ? null : status.desc;
    }
}
