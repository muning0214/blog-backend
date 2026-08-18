package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Article-Tag relation entity mapping to `article_tags` table.
 */
@Data
@TableName("article_tags")
public class ArticleTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private Long tagId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
