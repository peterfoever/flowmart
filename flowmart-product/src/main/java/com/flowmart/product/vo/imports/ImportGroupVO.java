package com.flowmart.product.vo.imports;

public record ImportGroupVO(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        Long id, String importItemKey, String importItemKeyNormalized,
        String spuName, Integer rowCount, String status, String errorCode, String errorMessage,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        Long spuId, String spuCode) {}
