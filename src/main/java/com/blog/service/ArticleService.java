package com.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.dto.ArticleDTO;
import com.blog.entity.Article;

/**
 * Article business service.
 */
public interface ArticleService {

    /** Public paginated list of published articles. */
    IPage<Article> pagePublished(int pageNum, int pageSize, String title, Long categoryId, Long tagId);

    /** Admin paginated list (includes drafts). */
    IPage<Article> pageAdmin(int pageNum, int pageSize, String title, Long categoryId, Integer status);

    /** Public archive page. */
    IPage<Article> pageArchive(int pageNum, int pageSize);

    /** Public article detail (increments view count). */
    Article getPublishedDetail(Long id);

    /** Admin article detail (no view increment). */
    Article getAdminDetail(Long id);

    /** Create an article. */
    Article create(ArticleDTO dto, Long userId);

    /** Update an article. */
    Article update(Long id, ArticleDTO dto);

    /** Logically delete an article. */
    void delete(Long id);
}
