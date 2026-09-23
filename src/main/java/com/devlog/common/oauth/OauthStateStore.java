package com.devlog.common.oauth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth 的 state 存储：防 CSRF，同时防「拿一个场景的 state 去走另一个场景」。
 *
 * <p>为什么必须有它：回调接口是公开的，任何人都能构造一个带 code 的请求打过来。
 * state 的作用是证明「这次回调对应的发起动作，确实是本服务刚刚发给这个浏览器的」。
 *
 * <p>为什么放内存：单实例部署下实现最简单，也不会因为外部存储抖动把登录搞挂。
 * 代价是**多实例部署时 state 不共享**——真到那一步把这两个方法换成 Redis 实现即可，
 * 调用方一行都不用改。
 *
 * <p>purpose 是刻意加的：登录与绑定共用同一套 GitHub 回调，
 * 如果不区分用途，一个用于登录的 state 就能被拿去绑定账号（反过来也一样）。
 */
@Component
public class OauthStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** 上限兜底，防止被刷爆内存 */
    private static final int MAX_ENTRIES = 10_000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> states = new ConcurrentHashMap<>();

    private record Entry(String provider, String purpose, long expiresAt) {
    }

    public String issue(String provider, String purpose) {
        prune();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, new Entry(provider, purpose, Instant.now().plus(TTL).toEpochMilli()));
        return state;
    }

    /**
     * 校验并立即消费：同一个 state 只能用一次，用完即删。
     * 这样即使回调被重放，第二次也一定失败（GitHub 那边的 code 本身也是单次有效的）。
     */
    public boolean consume(String provider, String purpose, String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Entry entry = states.remove(state);
        if (entry == null) {
            return false;
        }
        if (!entry.provider().equals(provider) || !entry.purpose().equals(purpose)) {
            return false;
        }
        return entry.expiresAt() >= System.currentTimeMillis();
    }

    private void prune() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        if (states.size() >= MAX_ENTRIES) {
            // 极端情况直接清空：这些都是 10 分钟内没走完的回调，重来一次成本很低
            states.clear();
        }
    }
}
