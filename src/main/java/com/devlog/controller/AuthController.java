package com.devlog.controller;

import com.devlog.common.Result;
import com.devlog.dto.ChangePasswordDTO;
import com.devlog.dto.GithubCallbackDTO;
import com.devlog.dto.LoginDTO;
import com.devlog.dto.RegisterDTO;
import com.devlog.service.AuthService;
import com.devlog.vo.GithubAuthorizeVO;
import com.devlog.vo.IdentityVO;
import com.devlog.vo.LoginVO;
import com.devlog.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。
 *
 * <p>哪些需要登录由 WebMvcConfig 的拦截器决定，不在方法上写注解：
 * /me、/password、/identities 以及 GitHub 的绑定/解绑需要令牌，
 * 而发起登录与登录回调必须是公开的 —— 否则用户还没登录就被挡在门外。
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

    /* ------------------------------------------------------------------ */
    /* GitHub 登录与账号绑定                                                */
    /* ------------------------------------------------------------------ */

    /**
     * GitHub 登录是否可用。
     * 前端据此决定要不要渲染登录按钮 —— 没配置就藏起来，
     * 比让用户点下去再弹一个「未配置」有用。
     */
    @GetMapping("/github/enabled")
    public Result<Boolean> githubEnabled() {
        return Result.ok(authService.githubEnabled());
    }

    /** 发起登录，返回授权地址，前端直接跳过去 */
    @PostMapping("/github/authorize")
    public Result<GithubAuthorizeVO> githubAuthorize() {
        return Result.ok(authService.buildGithubLoginUrl());
    }

    /**
     * 登录回调。
     *
     * <p>令牌通过响应体返回而不是拼在跳转 URL 上：
     * URL 会进浏览器历史、Referer 和各级日志，把长期有效的令牌放进去等于到处留副本。
     * 所以 GitHub 回调到的是前端页面，由前端把 code 交给这里换取令牌。
     */
    @PostMapping("/github/callback")
    public Result<LoginVO> githubCallback(@Valid @RequestBody GithubCallbackDTO dto) {
        return Result.ok("登录成功", authService.loginWithGithub(dto));
    }

    /** 当前账号已绑定的登录方式 */
    @GetMapping("/identities")
    public Result<List<IdentityVO>> identities() {
        return Result.ok(authService.listCurrentIdentities());
    }

    /** 发起绑定，返回授权地址（state 的用途与登录不同） */
    @PostMapping("/github/bind-authorize")
    public Result<GithubAuthorizeVO> githubBindAuthorize() {
        return Result.ok(authService.buildGithubBindUrl());
    }

    @PostMapping("/github/bind")
    public Result<Void> githubBind(@Valid @RequestBody GithubCallbackDTO dto) {
        authService.bindGithub(dto);
        return Result.ok("绑定成功", null);
    }

    @DeleteMapping("/github/bind")
    public Result<Void> githubUnbind() {
        authService.unbindGithub();
        return Result.ok("已解绑", null);
    }
}
