package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Article create/update payload. Carries tag ids separately so the
 * service can maintain the article_tags relation table.
 */
@Data
public class ArticleDTO {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String summary;

    @NotBlank(message = "内容不能为空")
    private String content;

    private Long categoryId;
    private String cover;
    private Integer status;

    private Integer isTop;

    private List<Long> tagIds;
}
