package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方登录身份。
 *
 * <p>刻意没有 deleted 字段。这张表是「第三方身份 → 本服务账号」的映射，
 * 核心约束是「同一个第三方身份全局唯一」（uk_identity_provider）。
 * 如果用逻辑删除，解绑再绑定会留下多行同一身份的 deleted=1 记录，
 * 直接撞唯一键 —— article_tags 当初也是同样的理由选择物理删除。
 *
 * <p>application.yml 里虽然配了全局 logic-delete-field: deleted，
 * 但 MyBatis-Plus 只对声明了该字段的实体生效，这里不写就不会被误加条件。
 */
@Data
@TableName("user_identities")
public class UserIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** github / wechat … */
    private String provider;

    /** 第三方侧的唯一标识。统一按字符串存：GitHub 是数字 id，微信是 openid，形状不同 */
    private String providerUserId;

    private String providerUsername;

    private String providerAvatar;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
