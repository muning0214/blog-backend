package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Article-Tag relation data access layer.
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * Delete all article-tag links for a given article.
     */
    int deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * Returns article ids linked to a given tag.
     */
    List<Long> selectArticleIdsByTagId(@Param("tagId") Long tagId);
}
