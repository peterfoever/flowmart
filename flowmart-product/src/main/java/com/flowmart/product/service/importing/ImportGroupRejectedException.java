package com.flowmart.product.service.importing;

import com.flowmart.product.enums.ProductErrorCode;

/** 只有明确的业务拒绝可在组事务回滚后记录失败并继续；其他异常中止批次。 */
public class ImportGroupRejectedException extends RuntimeException {
    private final String code;
    private final String field;
    public ImportGroupRejectedException(ProductErrorCode code, String field) {
        this(code.name(), code.getMessage(), field);
    }
    public ImportGroupRejectedException(String code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }
    public String code() { return code; }
    public String field() { return field; }
}
