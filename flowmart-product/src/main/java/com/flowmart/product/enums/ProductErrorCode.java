package com.flowmart.product.enums;

import com.flowmart.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ProductErrorCode implements ErrorCode {
    /**
     *     商品类目错误 (20001-29999)
     */

    CATEGORY_PARENT_NOT_FOUND(20001, "父类目不存在"),
    CATEGORY_PARENT_DISABLED(20002, "父类目已禁用，无法创建子类目"),
    CATEGORY_LEVEL_EXCEEDED(20003, "类目层级不能超过5级"),
    CATEGORY_NAME_DUPLICATE(20004, "同级类目名称已存在，请勿重复添加"),
    CATEGORY_NOT_FOUND(20005, "类目不存在"),
    CATEGORY_HAS_CHILDREN(20006, "类目下存在子类目，请先处理子类目"),
    CATEGORY_NAME_TOO_LONG(20007, "类目名称不能超过64个字符"),
    CATEGORY_STATUS_CHANGE_FAILED(20008, "类目状态变更失败"),
    CATEGORY_CREATE_FAILED(20009,"类目创建失败"),
    CATEGORY_REPARENT_NAME_DUPLICATE(20010, "子类目上提后名称与目标父类目下已有类目冲突"),
    PRODUCT_NOT_FOUND(20100, "商品不存在"),
    PRODUCT_OFF_SHELF(20101, "商品已下架"),
    PRODUCT_STOCK_INSUFFICIENT(20102, "商品库存不足"),
    CATEGORY_DELETE_FAILED(20011, "删除类目失败，请稍后重试"),
    CATEGORY_REPARENT_FAILED(20012, "子类目上提失败，请稍后重试"),
    CATEGORY_LEVEL_DECREASE_FAILED(20013, "层级调整失败，请稍后重试"),
    CATEGORY_MOVE_TO_SELF(20020, "不能将类目移动到自己"),
    CATEGORY_PARENT_UNCHANGED(20021, "类目已在目标父类目下，无需移动"),
    CATEGORY_MOVE_TO_DESCENDANT(20022, "不能将类目移动到包括自己的子类目下"),
    CATEGORY_MOVE_FAILED(20023, "移动类目失败，请稍后重试"),

    /**
     * 品牌服务错误码
     */
    BRAND_NOT_FOUND(20030,"品牌不存在"),
    BRAND_NAME_DUPLICATE(20031,"品牌名称已存在"),
    BRAND_DISABLED(20032,"品牌已禁用"),
    BRAND_BOUND_BY_CATEGORY(20033,"品牌已被类目绑定"),
    CATEGORY_NOT_LEAF(20034,"只能给叶子类目绑定品牌"),
    CATEGORY_BRAND_REPLACE_FAILED(20035,"类目品牌绑定替换失败"),
    BRAND_CREATE_FAILED(20036,"品牌创建失败，请稍后重试"),
    BRAND_UPDATE_FAILED(20037,"品牌修改失败，请稍后重试"),
    BRAND_STATUS_CHANGE_FAILED(20038,"品牌状态更新失败，请稍后重试"),
    BRAND_DELETE_FAILED(20039,"品牌删除失败，请稍后重试"),

    /**
     * spu商品错误码
     */
    SPU_NOT_FOUND(20041,"SPU 不存在"),
    SPU_CREATE_FAILED(20042,"SPU 创建失败"),
    SPU_UPDATE_FAILED(20043,"SPU 更新失败"),
    SPU_NOT_DRAFT(20044,"SPU 非草稿状态"),
    SPU_VERSION_CONFLICT(20045,"SPU 版本冲突"),
    CATEGORY_DISABLED(20046,"类目已禁用"),
    CATEGORY_BRAND_NOT_BOUND(20047,"品牌未绑定到该类目"),
    CATEGORY_IN_USE_BY_SPU(20048,"类目被商品引用，无法删除"),
    BRAND_IN_USE_BY_SPU(20049,"品牌被商品引用，无法删除"),
    CATEGORY_BRAND_IN_USE_BY_SPU(20050,"类目-品牌绑定被商品引用，无法解绑"),
    IMAGE_DUPLICATE(20051,"轮播图存在重复 URL"),
    MAIN_IMAGE_IN_CAROUSEL(20052,"主图不能同时出现在轮播图中"),
    SPEC_NAME_DUPLICATE(20053,"规格名重复"),
    SPEC_VALUE_DUPLICATE(20054,"同一规格内规格值重复"),

    /**
     * sku商品错误码
     */
    SPEC_VALUES_EMPTY(20061,"规格值列表不能为空"),
    TOO_MANY_COMBINATIONS(20062,"单个SPU最多生成1000个SKU"),
    SKU_ALREADY_GENERATED(20063,"SKU已经生成过了"),
    SKU_GENERATE_FAILED(20064,"SKU生成失败"),
    SKU_DATA_CONFLICT(20065,"SKU编码或规格组合冲突，请检查后重试"),
    SPU_SPECS_FROZEN(20066,"已生成SKU，不允许修改商品规格"),
    SKU_NOT_FOUND(20067,"SKU不存在"),
    SKU_VERSION_CONFLICT(20068,"SKU 已被其他人修改，请刷新后重试"),
    SKU_UPDATE_FAILED(20069,"SKU 更新失败"),

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
