package com.flowmart.product.vo.imports;

public record ImportRowErrorVO(Integer rowNum, String importItemKey, String field,
                               String errorCode, String errorMessage) {}
