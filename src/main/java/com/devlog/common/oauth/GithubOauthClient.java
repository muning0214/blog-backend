package com.devlog.common.oauth;

import com.devlog.common.exception.BusinessException;
import com.devlog.config.DevLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth 的最小客户端：换令牌、读用户、读已验证邮箱。
 *
 * <p>只有三件事，其余交给 Spring 的 RestClient，不引任何第三方 SDK ——
 * 一个 OAuth 客户端引入一堆传递依赖，性价比是负的。
 *
 * <p>对外抛出的错误刻意收敛过：GitHub 返回的原文（可能含 error_description、
 * 请求 id 等内部信息）只写日志，不回传给客户端。
 */
@Slf4j
@Component
public class GithubOauthClient {

    /** 从 GitHub 读到的身份信息。id 是数字，但统一按字符串承载 */
    public record GithubUser(String id, String login, String name, String avatarUrl) {
    }

    private final DevLogProperties properties;
    private final RestClient rest;

    public GithubOauthClient(DevLogProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    private DevLogProperties.Github config() {
        DevLogProperties.Github github = properties.github();
        if (github == null || !github.enabled()) {
            // 503 而不是 500：这不是服务出错，是「本部署没开这个功能」，
            // 前端据此把登录入口藏起来即可
            throw new BusinessException(503, "本站未配置 GitHub 登录");
        }
        return github;
    }

    /** 拼授权地址。redirect_uri 必须与 GitHub 后台登记的完全一致，否则会被拒 */
    public String buildAuthorizeUrl(String state) {
        DevLogProperties.Github c = config();
        return UriComponentsBuilder.fromUriString(c.authorizeUriOrDefault())
                .queryParam("client_id", c.clientId())
                .queryParam("redirect_uri", c.redirectUri())
                .queryParam("scope", c.scopeOrDefault())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * 用 code 换 access_token。
     * 必须显式 Accept: application/json —— 否则 GitHub 返回 URL 编码的表单串，很难解析。
     */
    @SuppressWarnings("unchecked")
    public String exchangeCode(String code) {
        DevLogProperties.Github c = config();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", c.clientId());
        form.add("client_secret", c.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", c.redirectUri());
        try {
            Map<String, Object> body = rest.post()
                    .uri(c.tokenUriOrDefault())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object token = body == null ? null : body.get("access_token");
            if (token == null || String.valueOf(token).isBlank()) {
                log.warn("GitHub 换取令牌失败: error={}", body == null ? "空响应" : body.get("error"));
                throw BusinessException.unauthorized("GitHub 授权失败，请重新登录");
            }
            return String.valueOf(token);
        } catch (RestClientException e) {
            log.warn("调用 GitHub 换取令牌异常: {}", e.getMessage());
            throw new BusinessException(503, "暂时无法连接 GitHub，请稍后再试");
        }
    }

    @SuppressWarnings("unchecked")
    public GithubUser fetchUser(String accessToken) {
        DevLogProperties.Github c = config();
        try {
            Map<String, Object> body = rest.get()
                    .uri(c.apiBaseOrDefault() + "/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("id") == null) {
                throw BusinessException.unauthorized("读取 GitHub 账号信息失败");
            }
            return new GithubUser(
                    text(body.get("id")),
                    text(body.get("login")),
                    text(body.get("name")),
                    text(body.get("avatar_url")));
        } catch (RestClientException e) {
            log.warn("调用 GitHub 读取用户信息异常: {}", e.getMessage());
            throw new BusinessException(503, "暂时无法连接 GitHub，请稍后再试");
        }
    }

    /**
     * 取用户邮箱，**只接受 GitHub 标记为 verified 的**。
     *
     * <p>这一点是账号安全的关键：只有已验证邮箱才允许与既有账号自动关联。
     * 否则任何人只要在 GitHub 上把资料邮箱改成你的地址（未验证也能填），
     * 就能顶替你的账号。
     *
     * <p>拿不到邮箱不影响登录，只是会按「全新账号」处理，所以这里失败返回 null 而不抛异常。
     */
    @SuppressWarnings("unchecked")
    public String fetchPrimaryVerifiedEmail(String accessToken) {
        DevLogProperties.Github c = config();
        try {
            List<Map<String, Object>> emails = rest.get()
                    .uri(c.apiBaseOrDefault() + "/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(List.class);
            if (emails == null) {
                return null;
            }
            String fallback = null;
            for (Map<String, Object> item : emails) {
                Object email = item.get("email");
                if (email == null || !Boolean.TRUE.equals(item.get("verified"))) {
                    continue;
                }
                if (Boolean.TRUE.equals(item.get("primary"))) {
                    return String.valueOf(email);
                }
                if (fallback == null) {
                    fallback = String.valueOf(email);
                }
            }
            return fallback;
        } catch (RestClientException e) {
            log.warn("读取 GitHub 邮箱失败（按新账号处理）: {}", e.getMessage());
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
