package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Comment data access layer.
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * Paginated admin query with optional status filter.
     */
    IPage<Comment> selectCommentPage(Page<Comment> page, @Param("status") Integer status);

    /**
     * Returns all approved comments (with children) for a given article.
     */
    List<Comment> selectApprovedCommentsByArticle(@Param("articleId") Long articleId);
}
