package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.Result;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.CommentMapper;
import com.blog.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin dashboard statistics endpoint.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CommentMapper commentMapper;

    /**
     * Returns summary counts for the admin dashboard.
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long articleCount = articleMapper.selectCount(null);
        long publishedCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, 1));
        long categoryCount = categoryMapper.selectCount(null);
        long tagCount = tagMapper.selectCount(null);
        long commentCount = commentMapper.selectCount(null);
        long pendingCommentCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, 0));

        // Sum of all article view counts
        long totalViews = 0L;
        for (Article a : articleMapper.selectList(null)) {
            if (a.getViewCount() != null) {
                totalViews += a.getViewCount();
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("articleCount", articleCount);
        data.put("publishedCount", publishedCount);
        data.put("categoryCount", categoryCount);
        data.put("tagCount", tagCount);
        data.put("commentCount", commentCount);
        data.put("pendingCommentCount", pendingCommentCount);
        data.put("totalViews", totalViews);
        return Result.success(data);
    }
}
