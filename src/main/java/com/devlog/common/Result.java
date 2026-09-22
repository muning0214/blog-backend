package com.devlog.common;

/**
 * 统一响应体。
 *
 * 注意与 status code 的分工：这里的 code 只是业务码的冗余副本，
 * 真正的状态码由 {@code GlobalExceptionHandler} 通过 ResponseEntity 返回的 HTTP 状态表达。
 * 之所以要强调：如果异常处理器直接返回本对象而不设 HTTP 状态，
 * 401 会变成 HTTP 200，前端基于 status 做的「token 过期就跳登录」就永远不会触发。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
