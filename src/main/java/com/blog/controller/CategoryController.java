package com.blog.controller;

import com.blog.common.Result;
import com.blog.entity.Category;
import com.blog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Category controller: public read + admin write.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ==================== Public endpoints ====================

    /**
     * Public list with article count.
     */
    @GetMapping("/api/categories")
    public Result<List<Category>> listWithCount() {
        return Result.success(categoryService.listWithCount());
    }

    // ==================== Admin endpoints ====================

    /**
     * Admin list all.
     */
    @GetMapping("/api/admin/categories")
    public Result<List<Category>> listAll() {
        return Result.success(categoryService.listAll());
    }

    /**
     * Create category.
     */
    @PostMapping("/api/admin/categories")
    public Result<Category> create(@RequestBody Category category) {
        return Result.success("创建成功", categoryService.create(category));
    }

    /**
     * Update category.
     */
    @PutMapping("/api/admin/categories/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        return Result.success("更新成功", categoryService.update(id, category));
    }

    /**
     * Delete category.
     */
    @DeleteMapping("/api/admin/categories/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success("删除成功", null);
    }
}
