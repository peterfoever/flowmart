package com.flowmart.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用错误码（模块号 1）。
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** 兜底错误，日志里一定伴随堆栈 */
    SYSTEM_ERROR(10000, "系统繁忙，请稍后重试"),
    PARAM_INVALID(10001, "参数校验失败"),
    RESOURCE_NOT_FOUND(10002, "资源不存在"),
    OPERATION_CONFLICT(10003, "操作冲突，请刷新后重试"),
    UNAUTHORIZED(10401, "未登录或登录已失效"),
    FORBIDDEN(10403, "无权限执行该操作"),
    RATE_LIMITED(10429, "请求过于频繁"),

    ;

    private final int code;
    private final String message;
}
