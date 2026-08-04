package com.flowmart.common.result;

import com.flowmart.common.exception.ErrorCode;
import com.flowmart.common.web.TraceIdFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应包装。
 * <p>
 * 约定：HTTP 状态码永远是 200（除非是网关/框架层错误），业务成败一律看 {@code code}。
 * 这样前端只需要一套解析逻辑，也方便网关做统一日志采集。
 *
 * @param <T> 业务数据类型
 */
@Data
@Schema(description = "统一响应结构")
public class R<T> implements Serializable {

    /** 成功码，固定为 0 */
    public static final int SUCCESS_CODE = 0;

    @Schema(description = "业务状态码，0 表示成功", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "ok")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "链路追踪 ID，排查线上问题时提供给运维", example = "3f2a1b9c8d7e6f50")
    private String traceId;

    private R() {
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = SUCCESS_CODE;
        r.message = "ok";
        r.data = data;
        r.traceId = currentTraceId();
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.traceId = currentTraceId();
        return r;
    }

    @Schema(hidden = true)
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }

    /** 从 MDC 取当前请求的 traceId，由 TraceIdFilter 写入 */
    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }
}
