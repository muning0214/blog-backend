package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.entity.Category;
import com.blog.mapper.CategoryMapper;
import com.blog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Category service implementation.
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> listWithCount() {
        return categoryMapper.selectCategoriesWithCount();
    }

    @Override
    public List<Category> listAll() {
        LambdaQueryWrapper<Category> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(Category::getSort).orderByAsc(Category::getId);
        return categoryMapper.selectList(qw);
    }

    @Override
    public Category create(Category category) {
        if (category.getSort() == null) {
            category.setSort(0);
        }
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public Category update(Long id, Category category) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        existing.setName(category.getName());
        existing.setSort(category.getSort() == null ? existing.getSort() : category.getSort());
        categoryMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        long count = categoryMapper.countPublishedArticles(id);
        if (count > 0) {
            throw new BusinessException(400, "该分类下还有 " + count + " 篇已发布文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }
}
