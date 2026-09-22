package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章与标签的多对多关联。
 *
 * <p>这张表没有 deleted 列，删除是物理删除。原因：表上有
 * {@code uk_article_tag(article_id, tag_id)} 唯一键，如果这里也做逻辑删除，
 * 那么「先去掉一个标签、再加回来」就会撞唯一键失败（旧行还在，只是被标记删除了）。
 */
@Data
@TableName("article_tags")
public class ArticleTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long tagId;

    private LocalDateTime createdAt;
}
