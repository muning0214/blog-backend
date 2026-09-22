package com.devlog.common.jwt;

import com.devlog.common.exception.BusinessException;
import com.devlog.config.DevLogProperties;
import com.devlog.entity.User;
import com.devlog.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：拦截 /api/author/** 与 /api/auth/me 这类需要登录的接口。
 *
 * <p>与常见的「解析 JWT 就放行」相比，这里多做了一步 —— <b>查库确认账号仍然有效</b>：
 *
 * <ul>
 *   <li>令牌只能证明「某个时刻这个人登录过」。签发之后如果账号被禁用或删除，
 *       只验签名的实现照样放行，等于封禁不生效。</li>
 *   <li>身份字段（昵称、角色）以数据库为准，而不是相信令牌里的副本，
 *       否则改角色也要等令牌过期才生效。</li>
 * </ul>
 *
 * <p>代价是每个鉴权请求多一次主键查询，对博客这种量级完全可接受。
 * 另外这里通过 {@code afterCompletion} 清理 ThreadLocal，
 * 避免 Tomcat 线程复用导致的身份串号。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final DevLogProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String headerName = properties.jwt().headerOrDefault();
        String prefix = properties.jwt().prefixOrDefault();
        String header = request.getHeader(headerName);

        if (!StringUtils.hasText(header) || !header.startsWith(prefix)) {
            throw BusinessException.unauthorized("未登录：请求缺少访问令牌");
        }

        String token = header.substring(prefix.length()).trim();
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            throw BusinessException.unauthorized("登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw BusinessException.unauthorized("访问令牌无效，请重新登录");
        }

        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw BusinessException.unauthorized("访问令牌无效，请重新登录");
        }

        // 逻辑删除的账号在这里查不到，status != 1 表示被禁用
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("账号不存在或已被注销，请重新登录");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }

        CurrentUser.set(new CurrentUser.Principal(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
