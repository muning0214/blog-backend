package com.devlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devlog.entity.UserIdentity;

/** 第三方登录身份。表结构见 blog-database/01_schema.sql 的 user_identities */
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
