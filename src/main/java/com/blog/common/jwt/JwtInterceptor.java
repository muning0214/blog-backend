package com.blog.common.jwt;

import com.blog.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts requests that require authentication; validates the JWT token.
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USERNAME = "currentUsername";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Allow preflight CORS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(401, "未登录或token缺失");
        }
        String token = header.substring(TOKEN_PREFIX.length());
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "token无效或已过期");
        }
        // Store user info in request for downstream controllers
        Claims claims = jwtUtil.parseToken(token);
        request.setAttribute(ATTR_USER_ID, claims.get("userId", Long.class));
        request.setAttribute(ATTR_USERNAME, claims.get("username", String.class));
        return true;
    }
}
