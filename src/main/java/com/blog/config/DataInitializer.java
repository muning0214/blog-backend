package com.blog.config;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.entity.User;
import com.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures the admin account has a correctly BCrypt-hashed password on startup,
 * so the SQL seed hash (which may not match) is always corrected to "admin123".
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserMapper userMapper;

    @Bean
    public ApplicationRunner initAdminPassword() {
        return args -> {
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            qw.eq(User::getUsername, "admin");
            User admin = userMapper.selectOne(qw);
            if (admin == null) {
                return;
            }
            // If the stored hash does not verify against "admin123", re-hash it.
            if (!BCrypt.checkpw("admin123", admin.getPassword())) {
                String hashed = BCrypt.hashpw("admin123");
                admin.setPassword(hashed);
                userMapper.updateById(admin);
                log.info("Admin password has been reset to BCrypt hash for 'admin123'.");
            } else {
                log.info("Admin password already correctly hashed.");
            }
        };
    }
}
