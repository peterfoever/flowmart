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
    CATEGORY_NOT_LEAF(20034,"只能给叶子类目绑定品牌、商品"),
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
    SPU_INVALID_STATE(20055,"SPU非草稿或下架状态，不允许上架"),
    SPU_NOT_ON_SHELF(20056,"SPU不是上架状态"),

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
    SKU_PRICE_INVALID(20070,"SKU 价格不合法"),
    SKU_IMAGE_REQUIRED(20071, "SKU 图片不能为空"),
    SKU_SPEC_MISMATCH(20072, "SKU 规格组合与 SPU 规格定义不一致"),

    // Excel导入商品错误码
    IMPORT_ITEM_KEY_INVALID(20080, "导入商品标识缺失或不合法"),
    GROUP_FIELD_INCONSISTENT(20081, "同组字段不一致"),
    GROUP_SPEC_COMBINATION_DUPLICATE(20082, "同组规格组合重复"),
    GROUP_NO_SPEC_MULTIPLE_ROWS(20083, "无规格商品只能有一行"),
    GROUP_SPEC_DEFINITION_INVALID(20084, "规格定义不合法"),
    PARSE_ROW_FAILED(20085, "组内存在解析失败的行，整组未导入"),
    IMPORT_FIELD_REQUIRED(20086, "导入字段不能为空"),
    IMPORT_TEXT_TOO_LONG(20087, "导入字段长度超过限制"),
    IMPORT_ID_NOT_TEXT(20088, "ID必须使用文本单元格"),
    IMPORT_ID_INVALID(20089, "ID必须是Long范围内的正整数"),
    SPEC_DEFINITION_INVALID(20090, "规格定义必须是符合结构要求的JSON数组"),
    SPEC_COMBINATION_INVALID(20091, "规格组合必须是符合结构要求的JSON数组"),
    CELL_FORMULA_NOT_ALLOWED(20092, "公式单元格不允许，请粘贴为值"),
    CELL_ERROR_VALUE(20093, "单元格为错误值"),
    IMPORT_CELL_TYPE_INVALID(20094, "单元格类型不符合字段要求"),
    IDEMPOTENCY_KEY_CONFLICT(20200, "同一幂等键不能用于不同导入内容"),
    IMPORT_BATCH_NOT_FOUND(20201, "导入批次不存在"),
    IMPORT_ITEM_DUPLICATED(20202, "该导入商品标识已成功导入，不支持覆盖"),
    IMPORT_ABORTED(20203, "导入中断，请查询批次结果，勿盲目重试"),
    IMPORT_FILE_INVALID(20204, "仅支持不超过5MiB的xlsx文件"),
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
