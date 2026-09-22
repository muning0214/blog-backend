package com.devlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建 / 更新文章的请求体。
 *
 * <p>tags 传的是标签 slug 列表，Service 负责把 slug 映射成 tag_id，
 * 并顺手补建还不存在的标签行 —— 前端不需要先调接口创建标签。
 */
public record ArticleSaveDTO(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 个字符")
        String title,

        @Size(max = 160, message = "链接标识不能超过 160 个字符")
        String slug,

        @Size(max = 500, message = "摘要不能超过 500 个字符")
        String summary,

        @NotBlank(message = "正文不能为空")
        String content,

        @Size(max = 500, message = "封面路径过长")
        String coverPath,

        List<String> tags,

        List<AttachmentInput> attachments,

        /** draft / published；为空时新建默认 draft，更新时保持原状态 */
        String status,

        Boolean featured) {

    /** 附件入参：字段名与前端 Attachment 类型一致 */
    public record AttachmentInput(
            @NotBlank(message = "附件名不能为空")
            @Size(max = 255, message = "附件名过长")
            String name,

            @NotBlank(message = "附件路径不能为空")
            @Size(max = 500, message = "附件路径过长")
            String path,

            Long size,

            @Size(max = 120, message = "MIME 类型过长")
            String mime) {
    }
}
