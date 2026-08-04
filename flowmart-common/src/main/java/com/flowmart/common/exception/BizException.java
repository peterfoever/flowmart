package com.flowmart.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 只用于「可预期」的业务分支，例如库存不足、状态流转非法。
 * 这类异常不打印堆栈，日志级别 WARN —— 它们是业务结果，不是故障。
 * 系统故障请直接抛原始异常，交给全局处理器兜底。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 需要携带上下文时使用，例如 "库存不足，当前可用 3，申请 10" */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 业务异常不需要堆栈，省下 fillInStackTrace 的开销 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static void check(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    public static void check(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new BizException(errorCode, message);
        }
    }
}
