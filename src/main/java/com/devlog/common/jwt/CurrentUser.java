package com.devlog.common.jwt;

import com.devlog.common.exception.BusinessException;

/**
 * 当前登录用户的请求级上下文。
 *
 * <p>用 ThreadLocal 承载，因此必须在请求结束时清理 —— 见
 * {@link JwtInterceptor#afterCompletion}。否则 Tomcat 的线程池会复用线程，
 * 上一个请求的身份会泄漏给下一个请求。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public record Principal(Long id, String email, String nickname, String role) {
    }

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static Principal require() {
        Principal principal = HOLDER.get();
        if (principal == null) {
            throw BusinessException.unauthorized("未登录或登录状态已失效");
        }
        return principal;
    }

    public static Long requireId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
