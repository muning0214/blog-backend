package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Article data access layer.
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * Paginated query that joins category and user to populate
     * categoryName / authorName / tagNames.
     */
    IPage<Article> selectArticlePage(Page<Article> page,
                                     @Param("title") String title,
                                     @Param("categoryId") Long categoryId,
                                     @Param("tagId") Long tagId,
                                     @Param("status") Integer status,
                                     @Param("publishedOnly") boolean publishedOnly);

    /**
     * Load a single article with joined category/author/tags by id.
     */
    Article selectArticleDetail(@Param("id") Long id);

    /**
     * List archived articles (id, title, createTime) grouped for archive view.
     */
    IPage<Article> selectArchivePage(Page<Article> page);
}
