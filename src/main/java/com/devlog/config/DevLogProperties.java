package com.devlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * devlog.* 配置项。
 *
 * <p>用 record 做构造器绑定，字段天然不可变；kebab-case 的配置键由 Spring 的宽松绑定
 * 自动映射到 camelCase 字段（例如 expiration-ms -> expirationMs）。
 */
@ConfigurationProperties(prefix = "devlog")
public record DevLogProperties(Jwt jwt, Upload upload, Cors cors, Site site, Github github) {

    public record Jwt(String secret, Long expirationMs, String header, String prefix) {
        public long expirationMsOrDefault() {
            return expirationMs == null || expirationMs <= 0 ? 86_400_000L : expirationMs;
        }

        public String headerOrDefault() {
            return header == null || header.isBlank() ? "Authorization" : header;
        }

        public String prefixOrDefault() {
            return prefix == null || prefix.isBlank() ? "Bearer " : prefix;
        }
    }

    public record Upload(String dir, String publicPrefix, Long maxImageBytes, Long maxFileBytes) {
        public String dirOrDefault() {
            return dir == null || dir.isBlank() ? "./uploads" : dir;
        }

        public String publicPrefixOrDefault() {
            return publicPrefix == null || publicPrefix.isBlank() ? "/uploads" : publicPrefix;
        }

        public long maxImageBytesOrDefault() {
            return maxImageBytes == null || maxImageBytes <= 0 ? 6L * 1024 * 1024 : maxImageBytes;
        }

        public long maxFileBytesOrDefault() {
            return maxFileBytes == null || maxFileBytes <= 0 ? 20L * 1024 * 1024 : maxFileBytes;
        }
    }

    /** 逗号分隔的允许来源列表；为空时表示不放开任何跨域来源。 */
    public record Cors(String allowedOrigins) {
    }

    public record Site(Boolean allowRegistration) {
        public boolean allowRegistrationOrDefault() {
            return allowRegistration == null || allowRegistration;
        }
    }

    /**
     * 第三方登录（当前只有 GitHub）。
     *
     * <p>clientId / clientSecret 只写在 application-local.yml 或环境变量里，不进仓库 ——
     * 和数据库密码同样的处理方式。三者缺一就视为「未启用」，接口返回 503，
     * 前端据此把 GitHub 登录按钮藏起来，而不是点了报错。
     *
     * <p>三个 URL 做成可配置不是为了「灵活」：是为了能用本地桩服务把整条 OAuth 链路
     * 端到端跑完（没有真实凭据时，否则只能靠人工点一遍）。默认值就是官方地址。
     */
    public record Github(String clientId, String clientSecret, String redirectUri,
                         String authorizeUri, String tokenUri, String apiBase, String scope) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank()
                    && redirectUri != null && !redirectUri.isBlank();
        }

        public String authorizeUriOrDefault() {
            return blankTo(authorizeUri, "https://github.com/login/oauth/authorize");
        }

        public String tokenUriOrDefault() {
            return blankTo(tokenUri, "https://github.com/login/oauth/access_token");
        }

        public String apiBaseOrDefault() {
            return blankTo(apiBase, "https://api.github.com");
        }

        public String scopeOrDefault() {
            // read:user 拿昵称与头像；user:email 才能读到已验证邮箱（用于与既有账号关联）
            return blankTo(scope, "read:user user:email");
        }

        private static String blankTo(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
