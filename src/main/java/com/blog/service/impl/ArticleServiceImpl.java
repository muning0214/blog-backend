package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.dto.ArticleDTO;
import com.blog.entity.Article;
import com.blog.entity.ArticleTag;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.ArticleTagMapper;
import com.blog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Article service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    public IPage<Article> pagePublished(int pageNum, int pageSize, String title, Long categoryId, Long tagId) {
        Page<Article> page = new Page<>(pageNum, pageSize);
        return articleMapper.selectArticlePage(page, title, categoryId, tagId, null, true);
    }

    @Override
    public IPage<Article> pageAdmin(int pageNum, int pageSize, String title, Long categoryId, Integer status) {
        Page<Article> page = new Page<>(pageNum, pageSize);
        return articleMapper.selectArticlePage(page, title, categoryId, null, status, false);
    }

    @Override
    public IPage<Article> pageArchive(int pageNum, int pageSize) {
        Page<Article> page = new Page<>(pageNum, pageSize);
        return articleMapper.selectArchivePage(page);
    }

    @Override
    public Article getPublishedDetail(Long id) {
        Article article = articleMapper.selectArticleDetail(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(404, "文章不存在");
        }
        // Increment view count
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id).setSql("view_count = view_count + 1");
        articleMapper.update(null, uw);
        article.setViewCount(article.getViewCount() == null ? 1 : article.getViewCount() + 1);
        return article;
    }

    @Override
    public Article getAdminDetail(Long id) {
        Article article = articleMapper.selectArticleDetail(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return article;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Article create(ArticleDTO dto, Long userId) {
        Article article = new Article();
        article.setUserId(userId);
        article.setCategoryId(dto.getCategoryId());
        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCover(dto.getCover());
        article.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        article.setIsTop(dto.getIsTop() == null ? 0 : dto.getIsTop());
        article.setViewCount(0L);
        articleMapper.insert(article);
        saveTags(article.getId(), dto.getTagIds());
        log.info("Article [{}] created by user [{}].", article.getId(), userId);
        return article;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Article update(Long id, ArticleDTO dto) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        existing.setTitle(dto.getTitle());
        existing.setSummary(dto.getSummary());
        existing.setContent(dto.getContent());
        existing.setCover(dto.getCover());
        existing.setCategoryId(dto.getCategoryId());
        existing.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        existing.setIsTop(dto.getIsTop() == null ? 0 : dto.getIsTop());
        articleMapper.updateById(existing);
        // Rebuild tag associations
        saveTags(id, dto.getTagIds());
        log.info("Article [{}] updated.", id);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        articleMapper.deleteById(id); // logical delete via @TableLogic
        log.info("Article [{}] logically deleted.", id);
    }

    /**
     * Replace the tag associations of an article.
     */
    private void saveTags(Long articleId, java.util.List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        if (CollectionUtils.isEmpty(tagIds)) {
            return;
        }
        for (Long tagId : tagIds) {
            ArticleTag at = new ArticleTag();
            at.setArticleId(articleId);
            at.setTagId(tagId);
            articleTagMapper.insert(at);
        }
    }
}
