package com.blog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.Result;
import com.blog.dto.CommentDTO;
import com.blog.entity.Comment;
import com.blog.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Comment controller: public submit/read + admin moderation.
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // ==================== Public endpoints ====================

    /**
     * Public: approved comment tree for an article.
     */
    @GetMapping("/api/comments/article/{articleId}")
    public Result<List<Comment>> treeByArticle(@PathVariable Long articleId) {
        return Result.success(commentService.treeByArticle(articleId));
    }

    /**
     * Public: submit a comment (becomes pending for review).
     */
    @PostMapping("/api/comments")
    public Result<Comment> submit(@Valid @RequestBody CommentDTO dto) {
        return Result.success("评论提交成功，待审核", commentService.submit(dto));
    }

    // ==================== Admin endpoints ====================

    /**
     * Admin: paginated comments with optional status filter.
     */
    @GetMapping("/api/admin/comments")
    public Result<IPage<Comment>> pageAdmin(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(commentService.pageAdmin(pageNum, pageSize, status));
    }

    /**
     * Admin: approve or reject a comment. Body: {"status": 1|2}
     */
    @PutMapping("/api/admin/comments/{id}/status")
    public Result<Comment> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        return Result.success("操作成功", commentService.updateStatus(id, status));
    }

    /**
     * Admin: logically delete a comment.
     */
    @DeleteMapping("/api/admin/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success("删除成功", null);
    }
}
