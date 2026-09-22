package com.devlog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 站点配置更新请求。全部字段可选，只更新传了值的部分。
 */
public record SettingsSaveDTO(
        @Size(max = 60, message = "站点名称不能超过 60 个字符")
        String siteName,

        @Size(max = 120, message = "副标题不能超过 120 个字符")
        String tagline,

        @Size(max = 60, message = "作者名不能超过 60 个字符")
        String authorName,

        @Size(max = 300, message = "作者简介不能超过 300 个字符")
        String authorBio,

        String aboutMd,

        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱过长")
        String email,

        @Size(max = 255, message = "GitHub 地址过长")
        String github) {
}
