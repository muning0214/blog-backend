package com.devlog.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * GitHub 回调参数。
 *
 * <p>code 是 GitHub 发的一次性授权码（5 分钟有效、只能用一次）；
 * state 是本服务在发起授权时签发、存在 OauthStateStore 里的随机串。
 * 两者缺一不可：code 证明「GitHub 认可了这次授权」，
 * state 证明「这次回调对应的发起动作来自本服务发给这个浏览器的页面」。
 */
public record GithubCallbackDTO(
        @NotBlank(message = "缺少授权码")
        String code,

        @NotBlank(message = "缺少 state")
        String state) {
}
