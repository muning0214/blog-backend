package com.devlog.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devlog.common.exception.BusinessException;
import com.devlog.common.jwt.CurrentUser;
import com.devlog.common.jwt.JwtUtil;
import com.devlog.common.oauth.GithubOauthClient;
import com.devlog.common.oauth.OauthStateStore;
import com.devlog.config.DevLogProperties;
import com.devlog.dto.ChangePasswordDTO;
import com.devlog.dto.GithubCallbackDTO;
import com.devlog.dto.LoginDTO;
import com.devlog.dto.RegisterDTO;
import com.devlog.entity.User;
import com.devlog.entity.UserIdentity;
import com.devlog.mapper.UserIdentityMapper;
import com.devlog.mapper.UserMapper;
import com.devlog.vo.GithubAuthorizeVO;
import com.devlog.vo.IdentityVO;
import com.devlog.vo.LoginVO;
import com.devlog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 认证服务：邮箱密码、第三方登录、账号绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String PROVIDER_GITHUB = "github";

    /** state 的用途。登录与绑定共用一套回调，必须区分，否则一个 state 能跨场景使用 */
    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_BIND = "bind";

    /**
     * 账号不存在时也要走一次 BCrypt 校验，让「邮箱不存在」与「密码错误」的
     * 响应时间接近，避免通过耗时差异枚举出哪些邮箱注册过。
     */
    private static final String DUMMY_HASH =
            "$2a$10$nU4iQ8uwf4G19ShKfWA7Kezxn5JBwG91AiwavTgUGh2ycJ.yhm80C";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final JwtUtil jwtUtil;
    private final DevLogProperties properties;
    private final OauthStateStore oauthStateStore;
    private final GithubOauthClient githubOauthClient;

    /* ------------------------------------------------------------------ */
    /* 邮箱密码                                                            */
    /* ------------------------------------------------------------------ */

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        if (!properties.site().allowRegistrationOrDefault()) {
            throw BusinessException.forbidden("当前站点未开放注册");
        }
        String email = normalizeEmail(dto.email());

        User exists = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (exists != null) {
            // 区分两种冲突：若已有账号没有密码，说明它是第三方登录创建的，
            // 这时提示「去用 GitHub 登录」比笼统的「已注册」有用得多
            if (exists.getPassword() == null) {
                throw BusinessException.conflict("该邮箱已用于第三方登录，请直接用 GitHub 登录后再设置密码");
            }
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

        // 账号不存在、或账号是第三方登录创建的（没有密码）→ 走同一条失败路径。
        // 「这个账号没有密码」不能被单独区分出来，否则等于告诉攻击者
        // 哪些邮箱是第三方登录账号。
        if (user == null || user.getPassword() == null) {
            BCrypt.checkpw(dto.password(), DUMMY_HASH);
            throw BusinessException.unauthorized("邮箱或密码不正确");
        }
        if (!BCrypt.checkpw(dto.password(), user.getPassword())) {
            throw BusinessException.unauthorized("邮箱或密码不正确");
        }
        assertEnabled(user);
        return new LoginVO(jwtUtil.issue(user), UserVO.from(user));
    }

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

        if (user.getPassword() != null) {
            // 账号已有密码：必须验旧密码（这里才校验，因为校验注解不知道账号有没有密码）
            if (dto.oldPassword() == null || dto.oldPassword().isBlank()) {
                throw BusinessException.badRequest("请输入当前密码");
            }
            if (!BCrypt.checkpw(dto.oldPassword(), user.getPassword())) {
                throw BusinessException.badRequest("当前密码不正确");
            }
            if (BCrypt.checkpw(dto.newPassword(), user.getPassword())) {
                throw BusinessException.badRequest("新密码不能与当前密码相同");
            }
            applyPassword(user.getId(), dto.newPassword());
            log.info("账号 {} 修改了密码", user.getId());
            return;
        }

        // 纯第三方登录的账号还没有密码，这里是「设置」而不是「修改」，
        // 所以不校验旧密码 —— 也无从校验。
        // 安全性来自当前请求已经带着有效令牌，身份在此之前已被证明过一次。
        applyPassword(user.getId(), dto.newPassword());
        log.info("账号 {} 设置了密码（此前仅有第三方登录）", user.getId());
    }

    private void applyPassword(Long userId, String rawPassword) {
        User patch = new User();
        patch.setId(userId);
        patch.setPassword(BCrypt.hashpw(rawPassword));
        userMapper.updateById(patch);
    }

    /* ------------------------------------------------------------------ */
    /* GitHub 登录                                                        */
    /* ------------------------------------------------------------------ */

    public boolean githubEnabled() {
        return properties.github() != null && properties.github().enabled();
    }

    /** 发起登录：签发 state 并拼出授权地址 */
    public GithubAuthorizeVO buildGithubLoginUrl() {
        String state = oauthStateStore.issue(PROVIDER_GITHUB, PURPOSE_LOGIN);
        return new GithubAuthorizeVO(githubOauthClient.buildAuthorizeUrl(state), state);
    }

    /** 发起绑定：同样签发 state，但用途不同 —— 绑定用的 state 不能拿去登录，反之亦然 */
    public GithubAuthorizeVO buildGithubBindUrl() {
        requireCurrent();
        String state = oauthStateStore.issue(PROVIDER_GITHUB, PURPOSE_BIND);
        return new GithubAuthorizeVO(githubOauthClient.buildAuthorizeUrl(state), state);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginWithGithub(GithubCallbackDTO dto) {
        GithubAuth auth = authorizeGithub(dto, PURPOSE_LOGIN);
        User user = resolveUser(auth);
        assertEnabled(user);
        return new LoginVO(jwtUtil.issue(user), UserVO.from(user));
    }

    /** 把 GitHub 身份绑到当前登录的账号上 */
    @Transactional(rollbackFor = Exception.class)
    public void bindGithub(GithubCallbackDTO dto) {
        User current = requireCurrent();
        GithubAuth auth = authorizeGithub(dto, PURPOSE_BIND);

        UserIdentity existing = findIdentity(auth.user().id());
        if (existing != null) {
            if (existing.getUserId().equals(current.getId())) {
                return; // 幂等：已经绑在自己名下
            }
            throw BusinessException.conflict("这个 GitHub 账号已经绑定了其它账号");
        }
        bindIdentity(current.getId(), auth.user());
        log.info("账号 {} 绑定了 GitHub {}", current.getId(), auth.user().login());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindGithub() {
        User current = requireCurrent();
        List<UserIdentity> identities = listIdentities(current.getId());
        UserIdentity target = identities.stream()
                .filter(i -> PROVIDER_GITHUB.equals(i.getProvider()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("当前账号没有绑定 GitHub"));

        // 关键守卫：不能摘掉最后一种登录方式。
        // 否则这个账号既没有密码、又没有任何第三方身份，变成谁也进不去的孤儿。
        if (current.getPassword() == null && identities.size() <= 1) {
            throw BusinessException.badRequest(
                    "这是当前账号唯一的登录方式，解绑后将无法登录。请先设置密码再解绑。");
        }
        userIdentityMapper.deleteById(target.getId());
        log.info("账号 {} 解绑了 GitHub", current.getId());
    }

    public List<IdentityVO> listCurrentIdentities() {
        return listIdentities(requireCurrent().getId()).stream().map(IdentityVO::from).toList();
    }

    /* ------------------------------------------------------------------ */
    /* 内部                                                                */
    /* ------------------------------------------------------------------ */

    /** 校验 state 并把 code 换成 GitHub 身份 */
    private GithubAuth authorizeGithub(GithubCallbackDTO dto, String purpose) {
        if (!oauthStateStore.consume(PROVIDER_GITHUB, purpose, dto.state())) {
            throw BusinessException.badRequest("授权已失效，请重新发起");
        }
        String accessToken = githubOauthClient.exchangeCode(dto.code());
        GithubOauthClient.GithubUser user = githubOauthClient.fetchUser(accessToken);
        return new GithubAuth(user, githubOauthClient.fetchPrimaryVerifiedEmail(accessToken));
    }

    /**
     * 把 GitHub 身份落成一个本服务的账号。
     * 三种情况按优先级走：已绑定 → 邮箱可关联 → 建新账号。
     */
    private User resolveUser(GithubAuth auth) {
        UserIdentity bound = findIdentity(auth.user().id());
        if (bound != null) {
            return requireById(bound.getUserId());
        }

        String email = normalizeEmail(auth.verifiedEmail());
        if (!email.isEmpty()) {
            User existing = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
            if (existing != null) {
                // 只认 GitHub 标记为 verified 的邮箱（fetchPrimaryVerifiedEmail 已过滤）。
                // 否则任何人在 GitHub 资料里填上你的邮箱（未验证也能填）就能顶替你的账号。
                bindIdentity(existing.getId(), auth.user());
                log.info("GitHub {} 自动关联到已有邮箱账号 {}", auth.user().login(), existing.getId());
                return existing;
            }
        }
        return createGithubUser(auth, email);
    }

    private User createGithubUser(GithubAuth auth, String verifiedEmail) {
        User user = new User();
        // email 列是 NOT NULL + 唯一。GitHub 用户可能没开放邮箱，
        // 这时用 GitHub 官方的 noreply 地址兜底 —— 它稳定、全局唯一，
        // 比塞空串或随机值语义清楚，也看得出这个账号来自哪。
        user.setEmail(verifiedEmail.isEmpty()
                ? auth.user().id() + "+" + safeLogin(auth.user()) + "@users.noreply.github.com"
                : verifiedEmail);
        user.setPassword(null);
        user.setNickname(truncate(nicknameOf(auth.user()), 60));
        user.setRole("AUTHOR");
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发首次登录：唯一键兜住，退回去用已存在的那条
            User existing = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, user.getEmail()));
            if (existing == null) {
                throw BusinessException.conflict("该邮箱已被占用，请改用其它方式登录");
            }
            bindIdentity(existing.getId(), auth.user());
            return existing;
        }
        bindIdentity(user.getId(), auth.user());
        log.info("通过 GitHub 创建账号: id={} login={}", user.getId(), auth.user().login());
        return user;
    }

    private void bindIdentity(Long userId, GithubOauthClient.GithubUser githubUser) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(PROVIDER_GITHUB);
        identity.setProviderUserId(githubUser.id());
        identity.setProviderUsername(truncate(githubUser.login(), 128));
        identity.setProviderAvatar(truncate(githubUser.avatarUrl(), 500));
        try {
            userIdentityMapper.insert(identity);
        } catch (DuplicateKeyException e) {
            // 并发绑定同一身份：唯一键兜住，忽略
            log.debug("身份已存在，忽略重复绑定: provider={} id={}", PROVIDER_GITHUB, githubUser.id());
        }
    }

    private UserIdentity findIdentity(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            return null;
        }
        return userIdentityMapper.selectOne(Wrappers.<UserIdentity>lambdaQuery()
                .eq(UserIdentity::getProvider, PROVIDER_GITHUB)
                .eq(UserIdentity::getProviderUserId, providerUserId));
    }

    private List<UserIdentity> listIdentities(Long userId) {
        return userIdentityMapper.selectList(Wrappers.<UserIdentity>lambdaQuery()
                .eq(UserIdentity::getUserId, userId)
                .orderByAsc(UserIdentity::getId));
    }

    private void assertEnabled(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }
    }

    /** GitHub 的昵称是用户自填的，属于不可信输入：截断后再入库 */
    private String nicknameOf(GithubOauthClient.GithubUser githubUser) {
        if (githubUser.name() != null && !githubUser.name().isBlank()) {
            return githubUser.name().trim();
        }
        if (githubUser.login() != null && !githubUser.login().isBlank()) {
            return githubUser.login();
        }
        return "GitHub 用户";
    }

    /** login 本来只允许字母数字与连字符，但既然是外部输入，拼进邮箱前再兜一层 */
    private String safeLogin(GithubOauthClient.GithubUser githubUser) {
        String login = githubUser.login() == null ? "" : githubUser.login().replaceAll("[^A-Za-z0-9-]", "");
        return login.isEmpty() ? "user" : login;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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

    /** GitHub 身份 + 其已验证邮箱（可能为空） */
    private record GithubAuth(GithubOauthClient.GithubUser user, String verifiedEmail) {
    }
}
