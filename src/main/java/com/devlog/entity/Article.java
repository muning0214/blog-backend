package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章。
 *
 * <p>tags / attachments / authorName 三个字段不是表列（都标了
 * {@code @TableField(exist = false)}），由 Service 在查询后组装。
 * 这样做的直接收益是：接口可以直接返回本实体，不需要再写一层 VO，
 * 因为 Jackson 的 SNAKE_CASE 策略已经把 camelCase 字段映射成与前端契约一致的键名。
 */
@Data
@TableName("articles")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者 users.id */
    private Long userId;

    private String title;

    /** URL 标识，全局唯一 */
    private String slug;

    private String summary;

    /** Markdown 原文 */
    private String content;

    /** 封面对象路径（不是完整 URL） */
    private String coverPath;

    /** draft / published */
    private String status;

    private Boolean featured;

    private Integer views;

    private Integer readingMinutes;

    private LocalDateTime publishedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ------------------------------------------------------------------
    // 以下为组装字段，不对应表列
    // ------------------------------------------------------------------

    /** 标签 slug 列表，前端按它做筛选与展示 */
    @TableField(exist = false)
    private List<String> tags = new ArrayList<>();

    /** 附件列表，字段形状与前端 Attachment 类型一致 */
    @TableField(exist = false)
    private List<Attachment> attachments = new ArrayList<>();

    /** 作者显示名 */
    @TableField(exist = false)
    private String authorName;

    /**
     * 附件在接口层的形状。
     *
     * <p>注意 size 对应的是表里的 size_bytes 列 —— 数据库里保留更具描述性的列名，
     * 接口层沿用原博客的契约（前端已经在用 size），转换只发生在组装附件列表的这一行。
     */
    public record Attachment(String name, String path, Long size, String mime) {
    }
}
