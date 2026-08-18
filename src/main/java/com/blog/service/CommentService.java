package com.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.dto.CommentDTO;
import com.blog.entity.Comment;

import java.util.List;

/**
 * Comment business service.
 */
public interface CommentService {

    /** Public: approved comments tree for an article. */
    List<Comment> treeByArticle(Long articleId);

    /** Public: submit a comment (status pending). */
    Comment submit(CommentDTO dto);

    /** Admin: paginated comments with optional status filter. */
    IPage<Comment> pageAdmin(int pageNum, int pageSize, Integer status);

    /** Admin: approve or reject a comment. */
    Comment updateStatus(Long id, Integer status);

    /** Admin: logically delete a comment. */
    void delete(Long id);
}
