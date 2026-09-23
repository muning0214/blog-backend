package com.devlog.vo;

/**
 * 授权地址。
 *
 * <p>为什么把 state 也一并回传：GitHub OAuth App 只允许登记**一个**回调地址，
 * 所以「登录」与「绑定」两条流程最终都会回到同一个前端页面。
 * 前端必须知道这次回调属于哪一条，才能决定调哪个接口 ——
 * 它用 state 作为键把意图存在 sessionStorage 里，回调页再取回来。
 *
 * <p>state 本来就在 authorizeUrl 里，单独再给一份是为了让前端不必去解 URL。
 */
public record GithubAuthorizeVO(String authorizeUrl, String state) {
}
