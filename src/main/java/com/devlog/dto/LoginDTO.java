package com.devlog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>请求体用 record：Jackson 原生支持 record 反序列化，且校验注解可以直接标在组件上，
 * 不需要 getter/setter 与一堆样板代码。只有要进 MyBatis XML 的查询对象才必须用带 getter 的类。
 */
public record LoginDTO(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "请输入密码")
        String password) {
}
