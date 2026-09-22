package com.devlog.controller;

import com.devlog.common.PageResult;
import com.devlog.common.Result;
import com.devlog.dto.ArticleQuery;
import com.devlog.entity.Article;
import com.devlog.service.ArticleService;
import com.devlog.vo.NeighboursVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的文章读取接口，匿名可访问。
 *
 * <p>ArticleQuery 作为方法参数由 Spring 自动从查询串绑定
 * （keyword / tag / page / size / featuredOnly），不需要 @ModelAttribute 注解。
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public Result<PageResult<Article>> list(ArticleQuery query) {
        return Result.ok(articleService.pagePublished(query));
    }

    @GetMapping("/{slug}")
    public Result<Article> detail(@PathVariable String slug) {
        // 只有这里计阅读量
        return Result.ok(articleService.readPublished(slug, true));
    }

    @GetMapping("/{slug}/related")
    public Result<List<Article>> related(@PathVariable String slug,
                                         @RequestParam(defaultValue = "3") int limit) {
        Article article = articleService.readPublished(slug, false);
        return Result.ok(articleService.related(article, limit));
    }

    @GetMapping("/{slug}/neighbours")
    public Result<NeighboursVO> neighbours(@PathVariable String slug) {
        Article article = articleService.readPublished(slug, false);
        return Result.ok(articleService.neighbours(article));
    }
}
