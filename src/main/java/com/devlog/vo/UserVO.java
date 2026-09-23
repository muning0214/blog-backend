package com.devlog.vo;

import com.devlog.entity.User;

/**
 * 对外的用户视图。
 *
 * <p>存在的唯一理由是不要漏掉 password：直接返回 User 实体虽然靠 {@code @JsonIgnore}
 * 也能挡住，但多一层显式的白名单更稳妥 —— 将来给 User 加字段时不会意外泄露。
 */
public record UserVO(Long id, String email, String nickname, String avatarPath, String bio, String role,
                     Boolean hasPassword) {

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarPath(),
                user.getBio(),
                user.getRole(),
                // 第三方登录创建的账号没有密码：「账号」页据此显示「设置密码」
                // 而不是「修改密码」，不然用户会卡在「当前密码」那一栏。
                user.getPassword() != null);
    }
}
