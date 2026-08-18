package com.blog.controller;

import com.blog.common.Result;
import com.blog.entity.Tag;
import com.blog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tag controller: public read + admin write.
 */
@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // ==================== Public endpoints ====================

    /**
     * Public list with article count.
     */
    @GetMapping("/api/tags")
    public Result<List<Map<String, Object>>> listWithCount() {
        return Result.success(tagService.listWithCount());
    }

    // ==================== Admin endpoints ====================

    /**
     * Admin list all.
     */
    @GetMapping("/api/admin/tags")
    public Result<List<Tag>> listAll() {
        return Result.success(tagService.listAll());
    }

    /**
     * Create tag.
     */
    @PostMapping("/api/admin/tags")
    public Result<Tag> create(@RequestBody Tag tag) {
        return Result.success("创建成功", tagService.create(tag));
    }

    /**
     * Update tag.
     */
    @PutMapping("/api/admin/tags/{id}")
    public Result<Tag> update(@PathVariable Long id, @RequestBody Tag tag) {
        return Result.success("更新成功", tagService.update(id, tag));
    }

    /**
     * Delete tag.
     */
    @DeleteMapping("/api/admin/tags/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success("删除成功", null);
    }
}
