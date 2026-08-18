package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Category data access layer.
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * Returns all categories with the count of published articles in each.
     */
    List<Category> selectCategoriesWithCount();

    /**
     * Returns the count of published articles for one category.
     */
    long countPublishedArticles(@Param("categoryId") Long categoryId);
}
