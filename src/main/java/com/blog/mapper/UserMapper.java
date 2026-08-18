package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User data access layer.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
