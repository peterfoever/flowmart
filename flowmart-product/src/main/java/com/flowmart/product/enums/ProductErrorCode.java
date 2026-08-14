package com.flowmart.product.enums;

import com.flowmart.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ProductErrorCode implements ErrorCode {
    // 商品类目错误 (20001-29999)

    CATEGORY_PARENT_NOT_FOUND(20001, "父类目不存在"),
    CATEGORY_PARENT_DISABLED(20002, "父类目已禁用，无法创建子类目"),
    CATEGORY_LEVEL_EXCEEDED(20003, "类目层级不能超过5级"),
    CATEGORY_NAME_DUPLICATE(20004, "同级类目名称已存在，请勿重复添加"),
    CATEGORY_NOT_FOUND(20005, "类目不存在"),
    CATEGORY_HAS_CHILDREN(20006, "类目下存在子类目，请先处理子类目"),
    CATEGORY_NAME_TOO_LONG(20007, "类目名称不能超过64个字符"),
    CATEGORY_STATUS_CHANGE_FAILED(20008, "类目状态变更失败"),
    CATEGORY_CREATE_FAILED(20009,"类目创建失败"),
    PRODUCT_NOT_FOUND(20100, "商品不存在"),
    PRODUCT_OFF_SHELF(20101, "商品已下架"),
    PRODUCT_STOCK_INSUFFICIENT(20102, "商品库存不足"),

    ;

    private final int code;
    private final String message;

    ProductErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    /**
     * 根据 code 获取枚举
     *
     * @param code 错误码
     * @return 枚举，找不到返回 null
     */
    public static ProductErrorCode fromCode(int code) {
        for (ProductErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 判断是否包含某个 code
     */
    public boolean matches(int code) {
        return this.code == code;
    }



}
