package com.devlog.controller;

import com.devlog.common.PageResult;
import com.devlog.common.Result;
import com.devlog.common.jwt.CurrentUser;
import com.devlog.dto.ArticleQuery;
import com.devlog.dto.ArticleSaveDTO;
import com.devlog.dto.SettingsSaveDTO;
import com.devlog.dto.TagSaveDTO;
import com.devlog.entity.Article;
import com.devlog.entity.SiteSettings;
import com.devlog.entity.Tag;
import com.devlog.service.ArticleService;
import com.devlog.service.AuthService;
import com.devlog.service.SettingsService;
import com.devlog.service.TagService;
import com.devlog.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者侧的全部接口，统一挂在 /api/author/** 下。
 *
 * <p>这个前缀被 JwtInterceptor 整体拦截，所以这里的每个方法都可以放心地
 * 直接取 {@link CurrentUser#requireId()} —— 拿不到身份根本进不来。
 *
 * <p>权限边界写在 Service 里而不是这里：控制器只负责参数与调用，
 * 「这篇文章是不是你的」这类判断集中在 ArticleService.requireOwned，
 * 不会因为将来多加一个入口就漏掉校验。
 */
@RestController
@RequestMapping("/api/author")
@RequiredArgsConstructor
public class AuthorController {

    private final ArticleService articleService;
    private final TagService tagService;
    private final SettingsService settingsService;
    private final AuthService authService;

    // ------------------------------ 文章 ------------------------------

    /** 我的文章列表（含草稿），可按 status / keyword 筛选 */
    @GetMapping("/articles")
    public Result<PageResult<Article>> myArticles(ArticleQuery query) {
        return Result.ok(articleService.pageForAuthor(CurrentUser.requireId(), query));
    }

    /** 按 id 取自己的文章用于编辑（含正文与草稿） */
    @GetMapping("/articles/{id}")
    public Result<Article> myArticle(@PathVariable Long id) {
        return Result.ok(articleService.getEditable(id, CurrentUser.requireId()));
    }

    @PostMapping("/articles")
    public Result<Article> create(@Valid @RequestBody ArticleSaveDTO dto) {
        return Result.ok("已保存", articleService.create(CurrentUser.requireId(), dto));
    }

    @PutMapping("/articles/{id}")
    public Result<Article> update(@PathVariable Long id, @Valid @RequestBody ArticleSaveDTO dto) {
        return Result.ok("已保存", articleService.update(id, CurrentUser.requireId(), dto));
    }

    @DeleteMapping("/articles/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id, CurrentUser.requireId());
        return Result.ok("已删除", null);
    }

    // ------------------------------ 标签 ------------------------------

    @GetMapping("/tags")
    public Result<List<Tag>> tags() {
        return Result.ok(tagService.listAll());
    }

    @PostMapping("/tags")
    public Result<Tag> createTag(@Valid @RequestBody TagSaveDTO dto) {
        return Result.ok(tagService.create(CurrentUser.requireId(), dto));
    }

    @PutMapping("/tags/{id}")
    public Result<Tag> updateTag(@PathVariable Long id, @Valid @RequestBody TagSaveDTO dto) {
        return Result.ok(tagService.update(id, CurrentUser.requireId(), dto));
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id, CurrentUser.requireId());
        return Result.ok("已删除", null);
    }

    // ------------------------------ 站点配置 ------------------------------

    @PutMapping("/settings")
    public Result<SiteSettings> saveSettings(@Valid @RequestBody SettingsSaveDTO dto) {
        return Result.ok("站点信息已保存", settingsService.save(dto, CurrentUser.requireId()));
    }

    // ------------------------------ 个人资料 ------------------------------

    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(UserVO.from(authService.requireCurrent()));
    }
}
