package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者账号。
 *
 * <p>password 加了 {@code @JsonIgnore}：这个实体有可能被直接序列化返回，
 * 一旦漏掉这个注解，BCrypt 哈希就会跟着接口发出去。
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    /** BCrypt 哈希。永不序列化。 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatarPath;

    private String bio;

    /** AUTHOR / ADMIN */
    private String role;

    /** 0-禁用 1-启用 */
    private Integer status;

    @JsonIgnore
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
