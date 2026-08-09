package com.flowmart.product.enums;




public enum CategoryStatus {
    STATUS(1,"启用");

    private final int code;
    private final String desc;


    CategoryStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
