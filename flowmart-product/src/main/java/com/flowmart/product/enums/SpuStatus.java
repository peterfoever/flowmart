package com.flowmart.product.enums;

import java.util.Arrays;
import java.util.Objects;

public enum SpuStatus {
    DRAFT(0, "草稿"),
    ON_SHELF(1, "上架"),
    OFF_SHELF(2, "下架");

    private final Integer code;
    private final String desc;

    SpuStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    /**
     * 根据 code 获取枚举
     *
     * @param code 状态码
     * @return 枚举，找不到返回 null
     */
    public static SpuStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SpuStatus status : values()) {
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
     * 判断前端传来的 0/1/2 是否合法
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
        SpuStatus status = fromCode(code);
        return status == null ? null : status.desc;
    }
}
