package com.blog.config;

import com.blog.common.jwt.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the JWT interceptor for admin-protected routes.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/admin/**")           // protect all admin endpoints
                .addPathPatterns("/api/user/**")            // protect user-specific endpoints
                .excludePathPatterns(
                        "/api/articles",                    // public list
                        "/api/articles/*",                   // public detail
                        "/api/articles/page/**",
                        "/api/categories",
                        "/api/categories/*",
                        "/api/tags",
                        "/api/comments/article/**",
                        "/api/auth/**"
                );
    }
}
