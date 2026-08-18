package com.blog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.Result;
import com.blog.common.jwt.JwtInterceptor;
import com.blog.dto.ArticleDTO;
import com.blog.entity.Article;
import com.blog.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Article controller: public read endpoints under /api/articles and
 * admin write endpoints under /api/admin/articles.
 */
@RestController
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    // ==================== Public endpoints ====================

    /**
     * Public paginated list of published articles.
     */
    @GetMapping("/api/articles")
    public Result<IPage<Article>> pagePublished(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId) {
        return Result.success(articleService.pagePublished(pageNum, pageSize, title, categoryId, tagId));
    }

    /**
     * Public archive list.
     */
    @GetMapping("/api/articles/archives")
    public Result<IPage<Article>> archive(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(articleService.pageArchive(pageNum, pageSize));
    }

    /**
     * Public article detail (increments views).
     */
    @GetMapping("/api/articles/{id}")
    public Result<Article> detail(@PathVariable Long id) {
        return Result.success(articleService.getPublishedDetail(id));
    }

    // ==================== Admin endpoints ====================

    /**
     * Admin paginated list (includes drafts).
     */
    @GetMapping("/api/admin/articles")
    public Result<IPage<Article>> pageAdmin(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return Result.success(articleService.pageAdmin(pageNum, pageSize, title, categoryId, status));
    }

    /**
     * Admin article detail (no view increment).
     */
    @GetMapping("/api/admin/articles/{id}")
    public Result<Article> adminDetail(@PathVariable Long id) {
        return Result.success(articleService.getAdminDetail(id));
    }

    /**
     * Create article.
     */
    @PostMapping("/api/admin/articles")
    public Result<Article> create(@Valid @RequestBody ArticleDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        return Result.success("创建成功", articleService.create(dto, userId));
    }

    /**
     * Update article.
     */
    @PutMapping("/api/admin/articles/{id}")
    public Result<Article> update(@PathVariable Long id, @Valid @RequestBody ArticleDTO dto) {
        return Result.success("更新成功", articleService.update(id, dto));
    }

    /**
     * Logically delete article.
     */
    @DeleteMapping("/api/admin/articles/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success("删除成功", null);
    }
}
