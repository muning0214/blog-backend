package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章附件。取代原云端设计里的 {@code attachments JSONB} 列。
 *
 * <p>列名用 size_bytes 而不是 size：语义更明确，也避开与 SQL 关键字擦边的写法。
 * 接口层对外仍然叫 size，转换在组装 {@link Article.Attachment} 时发生。
 */
@Data
@TableName("article_attachments")
public class ArticleAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    /** 原始文件名，仅用于展示 */
    private String name;

    /** 磁盘上的对象路径（随机名） */
    private String path;

    private Long sizeBytes;

    private String mime;

    private Integer sort;

    private LocalDateTime createdAt;
}
