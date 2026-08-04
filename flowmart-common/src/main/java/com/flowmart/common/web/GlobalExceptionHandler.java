package com.flowmart.common.web;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * 分级原则 —— 这条线在实际工作中非常重要，直接决定了你能不能从告警里筛出真问题：
 * <ul>
 *   <li>业务异常  → WARN，不打堆栈。属于正常业务分支，不该触发告警。</li>
 *   <li>参数异常  → WARN，不打堆栈。属于调用方问题。</li>
 *   <li>未知异常  → ERROR + 完整堆栈。这才是需要值班同学起床看的。</li>
 * </ul>
 * 对外一律返回脱敏后的兜底文案，绝不把原始异常信息（可能含 SQL、内网地址）吐给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[业务异常] uri={} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] uri={} detail={}", request.getRequestURI(), detail);
        return R.fail(CommonErrorCode.PARAM_INVALID, detail);
    }

    /** 表单/Query 对象绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        String detail = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] uri={} detail={}", request.getRequestURI(), detail);
        return R.fail(CommonErrorCode.PARAM_INVALID, detail);
    }

    /** 方法参数上的 @Validated 校验失败（如 @PathVariable @Min(1)） */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[约束校验失败] uri={} detail={}", request.getRequestURI(), detail);
        return R.fail(CommonErrorCode.PARAM_INVALID, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[缺少必填参数] uri={} param={}", request.getRequestURI(), e.getParameterName());
        return R.fail(CommonErrorCode.PARAM_INVALID, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[请求体解析失败] uri={} msg={}", request.getRequestURI(), e.getMessage());
        return R.fail(CommonErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[请求方法不支持] uri={} method={}", request.getRequestURI(), e.getMethod());
        return R.fail(CommonErrorCode.PARAM_INVALID, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        return R.fail(CommonErrorCode.RESOURCE_NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 静态资源找不到时抛的异常（Spring 6.1+）。
     * <p>
     * 为什么要单独处理它：我们保留了静态资源映射（Knife4j 的 /doc.html 依赖它），
     * 因此未匹配的请求会先落到 ResourceHttpRequestHandler，抛出的是本异常而不是
     * {@link NoHandlerFoundException}。不接住它，前端拿到的就是 Spring 默认错误页，
     * 而不是我们约定的 {@code R} 结构。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        return R.fail(CommonErrorCode.RESOURCE_NOT_FOUND, "接口不存在: /" + e.getResourcePath());
    }

    /**
     * 兜底。任何走到这里的异常都意味着代码没考虑到，属于线上事故候选。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] uri={} msg={}", request.getRequestURI(), e.getMessage(), e);
        return R.fail(CommonErrorCode.SYSTEM_ERROR);
    }
}
