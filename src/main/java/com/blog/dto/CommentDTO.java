package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Comment submit payload. Guests can comment without login.
 */
@Data
public class CommentDTO {

    @NotNull(message = "文章id不能为空")
    private Long articleId;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    private String email;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentId;
}
