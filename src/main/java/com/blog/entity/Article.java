package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Article entity mapping to `articles` table.
 * Non-persistent fields (categoryName, tags, authorName) are populated via
 * custom SQL joins and marked with @TableField(exist = false).
 */
@Data
@TableName("articles")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long categoryId;
    private String title;
    private String summary;
    private String content;
    private String cover;
    private Long viewCount;
    private Integer status;
    private Integer isTop;

    @TableLogic
    private Integer deleted;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---- Non-persistent fields (populated by joins) ----
    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String authorName;

    @TableField(exist = false)
    private String tagNames;
}
