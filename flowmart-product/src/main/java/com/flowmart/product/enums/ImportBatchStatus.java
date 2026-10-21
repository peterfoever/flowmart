package com.flowmart.product.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImportBatchStatus {
    PENDING("PENDING", "已创建，等待处理"),
    RUNNING("RUNNING", "处理中"),
    SUCCEEDED("SUCCEEDED", "全部成功"),
    PARTIAL("PARTIAL", "部分成功"),
    FAILED("FAILED", "全部失败或文件级拒绝"),
    INTERRUPTED("INTERRUPTED", "处理中断，存在未处理组"),
    ;
    private final String code;
    private final String description;

    public static ImportBatchStatus of(String code) {
        for (ImportBatchStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知导入批次状态: " + code);
    }
}
