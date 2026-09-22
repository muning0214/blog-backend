package com.devlog.controller;

import com.devlog.common.Result;
import com.devlog.dto.ChangePasswordDTO;
import com.devlog.dto.LoginDTO;
import com.devlog.dto.RegisterDTO;
import com.devlog.service.AuthService;
import com.devlog.vo.LoginVO;
import com.devlog.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>/me 与 /password 需要登录，由 WebMvcConfig 里的拦截器保护，不需要在方法上写注解。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok("注册成功", authService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok("登录成功", authService.login(dto));
    }

    /**
     * 登出。令牌是无状态的 JWT，服务端没有会话可销毁，
     * 前端丢掉本地令牌即可；保留这个端点是为了让前端有一个明确的调用目标，
     * 将来若要加「令牌黑名单」也不用改前端。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(UserVO.from(authService.requireCurrent()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return Result.ok("密码已更新", null);
    }
}
