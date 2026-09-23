package com.devlog.vo;

import com.devlog.entity.UserIdentity;

import java.time.LocalDateTime;

/** 已绑定的登录方式，给账号页展示用 */
public record IdentityVO(
        Long id,
        String provider,
        String providerUsername,
        String providerAvatar,
        LocalDateTime createdAt) {

    public static IdentityVO from(UserIdentity identity) {
        if (identity == null) {
            return null;
        }
        return new IdentityVO(
                identity.getId(),
                identity.getProvider(),
                identity.getProviderUsername(),
                identity.getProviderAvatar(),
                identity.getCreatedAt());
    }
}
