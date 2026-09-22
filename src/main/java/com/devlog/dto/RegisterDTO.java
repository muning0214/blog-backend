package com.devlog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>密码上限刻意设成 72：BCrypt 只取前 72 字节参与运算，
 * 更长的部分会被静默截断，与其让用户以为设了长密码更安全，不如直接限制。
 */
public record RegisterDTO(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱过长")
        String email,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码长度需在 8 到 72 位之间")
        String password,

        @Size(max = 60, message = "昵称不能超过 60 个字符")
        String nickname) {
}
