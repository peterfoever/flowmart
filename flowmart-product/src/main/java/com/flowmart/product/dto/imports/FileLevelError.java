package com.flowmart.product.dto.imports;

/** 文件级拒绝原因；不同于单行或单个商品组的业务错误。 */
public record FileLevelError(String errorCode, String errorMessage) {
    public FileLevelError {
        if (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("文件级错误码和说明不能为空");
        }
    }
}
