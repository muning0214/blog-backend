package com.devlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改或设置密码。
 *
 * <p>oldPassword 刻意**不加 @NotBlank**：第三方登录创建的账号还没有密码，
 * 前端「设置密码」时拿不出当前密码（也无从输入）。改成可选之后由业务层判断：
 * 账号有密码就必须校验旧密码；没有密码则视为「设置」，跳过校验。
 *
 * <p>这个区分必须放在业务层而不是校验层 —— 校验注解不知道请求者有没有密码。
 * 早先把 oldPassword 标成必填，结果是第三方账号根本设置不了密码，
 * 也就无法脱离「只能用 GitHub 登录」的状态（由端到端测试发现）。
 */
public record ChangePasswordDTO(
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 72, message = "新密码长度需在 8 到 72 位之间")
        String newPassword) {
}
