package com.devlog.vo;

/**
 * 登录 / 注册成功后的返回体。
 */
public record LoginVO(String token, UserVO user) {
}
