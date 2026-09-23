package com.devlog.config;

import com.devlog.common.jwt.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 层装配：鉴权拦截、上传目录的静态映射、跨域策略。
 *
 * <p>跨域这里有意收敛：只放开配置里列出的具体来源，且 {@code allowCredentials(false)}。
 * 原 test1 项目用的是「任意来源 pattern + 允许携带凭据」，那等于允许任何网站
 * 从访客浏览器里带着凭据调你的接口。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final DevLogProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只保护需要登录的路径；公开的 /api/articles、/api/tags、/api/settings 不拦截。
        //
        // 第三方登录这块要分清楚：发起登录（authorize）与回调（callback）必须公开，
        // 否则用户还没登录就被拦截器挡在门外，永远进不来；
        // 而「列出/绑定/解绑登录方式」操作的是当前账号，必须走鉴权。
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/author/**")
                .addPathPatterns("/api/auth/me")
                .addPathPatterns("/api/auth/password")
                .addPathPatterns("/api/auth/identities")
                .addPathPatterns("/api/auth/github/bind")
                .addPathPatterns("/api/auth/github/bind-authorize");
    }

    /**
     * 把上传目录映射成公开可读的静态资源。
     *
     * <p>这是相对原云端版本的一个改进：以前文件放在对象存储里，读权限只授予登录用户，
     * 于是匿名访客看不到博客图片。现在文件在自己的磁盘上，可以按目录整体对外开放，
     * 图片 URL 稳定可缓存，Markdown 里直接写路径即可。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = properties.upload().publicPrefixOrDefault();
        Path dir = Paths.get(properties.upload().dirOrDefault()).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String allowed = properties.cors() == null ? null : properties.cors().allowedOrigins();
        if (allowed == null || allowed.isBlank()) {
            // 未配置就不放开任何跨域。开发期前端走 Vite 代理，本来也不需要跨域。
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowed.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 前端用 Authorization 头带令牌，不用 Cookie，所以不需要允许携带凭据
                .allowCredentials(false)
                .maxAge(3600);
    }
}
