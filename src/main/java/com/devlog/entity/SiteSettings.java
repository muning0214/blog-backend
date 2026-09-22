package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点配置（单行表）。公开可读，只有作者能改。
 */
@Data
@TableName("site_settings")
public class SiteSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 最后一次修改者 */
    private Long userId;

    private String siteName;

    private String tagline;

    private String authorName;

    private String authorBio;

    /** 关于页 Markdown 原文 */
    private String aboutMd;

    private String avatarPath;

    private String email;

    private String github;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
