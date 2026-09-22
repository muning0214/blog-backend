package com.devlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * devlog.* 配置项。
 *
 * <p>用 record 做构造器绑定，字段天然不可变；kebab-case 的配置键由 Spring 的宽松绑定
 * 自动映射到 camelCase 字段（例如 expiration-ms -> expirationMs）。
 */
@ConfigurationProperties(prefix = "devlog")
public record DevLogProperties(Jwt jwt, Upload upload, Cors cors, Site site) {

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
}
