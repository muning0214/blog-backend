package com.devlog.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devlog.common.exception.BusinessException;
import com.devlog.common.jwt.CurrentUser;
import com.devlog.common.jwt.JwtUtil;
import com.devlog.config.DevLogProperties;
import com.devlog.dto.ChangePasswordDTO;
import com.devlog.dto.LoginDTO;
import com.devlog.dto.RegisterDTO;
import com.devlog.entity.User;
import com.devlog.mapper.UserMapper;
import com.devlog.vo.LoginVO;
import com.devlog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 账号服务：注册、登录、改密。
 *
 * <p>密码用 BCrypt（cost=10）存储。这里刻意没有「启动时把密码重置成默认值」这类补偿逻辑 ——
 * 原 test1 项目之所以需要它，是因为种子数据里的哈希是手写的假值，验证不过 admin123，
 * 于是不得不每次启动重写一遍，副作用是管理员永远改不了密码。
 * 正确做法是种子里放真实生成的哈希，代码里什么都不用做。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 用于「账号不存在」时也走一遍校验，抹平响应时间差，
     * 避免通过登录耗时判断某个邮箱是否注册过。
     */
    private static final String DUMMY_HASH =
            "$2a$10$nU4iQ8uwf4G19ShKfWA7Kezxn5JBwG91AiwavTgUGh2ycJ.yhm80C";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final DevLogProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        if (!properties.site().allowRegistrationOrDefault()) {
            throw BusinessException.forbidden("当前站点未开放注册");
        }
        String email = normalizeEmail(dto.email());

        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("该邮箱已注册，请直接登录");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(dto.password()));
        user.setNickname(defaultNickname(dto, email));
        user.setRole("AUTHOR");
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同一邮箱时唯一键会兜住
            throw BusinessException.conflict("该邮箱已注册，请直接登录");
        }
        log.info("新账号注册成功: id={}", user.getId());
        return new LoginVO(jwtUtil.issue(user), UserVO.from(user));
    }

    public LoginVO login(LoginDTO dto) {
        String email = normalizeEmail(dto.email());
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));

        // 账号不存在时也做一次 BCrypt 校验，保持响应时间一致
        if (user == null) {
            BCrypt.checkpw(dto.password(), DUMMY_HASH);
            throw BusinessException.unauthorized("邮箱或密码不正确");
        }
        if (!BCrypt.checkpw(dto.password(), user.getPassword())) {
            throw BusinessException.unauthorized("邮箱或密码不正确");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }
        return new LoginVO(jwtUtil.issue(user), UserVO.from(user));
    }

    /** 取当前登录用户（拦截器已保证存在） */
    public User requireCurrent() {
        return requireById(CurrentUser.requireId());
    }

    public User requireById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.unauthorized("账号不存在或已被注销");
        }
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        User user = requireCurrent();
        if (!BCrypt.checkpw(dto.oldPassword(), user.getPassword())) {
            throw BusinessException.badRequest("当前密码不正确");
        }
        if (BCrypt.checkpw(dto.newPassword(), user.getPassword())) {
            throw BusinessException.badRequest("新密码不能与当前密码相同");
        }
        User patch = new User();
        patch.setId(user.getId());
        patch.setPassword(BCrypt.hashpw(dto.newPassword()));
        userMapper.updateById(patch);
        log.info("账号 {} 修改了密码", user.getId());
    }

    private String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultNickname(RegisterDTO dto, String email) {
        if (dto.nickname() != null && !dto.nickname().isBlank()) {
            return dto.nickname().trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
