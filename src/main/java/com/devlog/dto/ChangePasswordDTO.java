package com.devlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求（需要旧密码校验）。
 */
public record ChangePasswordDTO(
        @NotBlank(message = "请输入当前密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 72, message = "新密码长度需在 8 到 72 位之间")
        String newPassword) {
}
