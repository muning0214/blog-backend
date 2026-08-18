package com.blog.service;

import com.blog.entity.Category;

import java.util.List;

/**
 * Category business service.
 */
public interface CategoryService {

    /** List all categories with published article count (public). */
    List<Category> listWithCount();

    /** List all categories for admin (no count needed). */
    List<Category> listAll();

    /** Create a category. */
    Category create(Category category);

    /** Update a category. */
    Category update(Long id, Category category);

    /** Logically delete a category. */
    void delete(Long id);
}
