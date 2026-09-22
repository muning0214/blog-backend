package com.devlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 更新标签的请求体。
 */
public record TagSaveDTO(
        @NotBlank(message = "标签名不能为空")
        @Size(max = 60, message = "标签名不能超过 60 个字符")
        String name,

        @Size(max = 60, message = "标签标识不能超过 60 个字符")
        String slug,

        @Size(max = 300, message = "标签描述不能超过 300 个字符")
        String description,

        /** 只接受 #RRGGBB，颜色值会直接进样式，必须校验形状 */
        @Pattern(regexp = "^#(?:[0-9a-fA-F]{6})$", message = "颜色需为 #RRGGBB 格式")
        String color) {
}
