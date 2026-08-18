package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.dto.CommentDTO;
import com.blog.entity.Comment;
import com.blog.mapper.CommentMapper;
import com.blog.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comment service implementation.
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    @Override
    public List<Comment> treeByArticle(Long articleId) {
        List<Comment> flat = commentMapper.selectApprovedCommentsByArticle(articleId);
        return buildTree(flat);
    }

    @Override
    public Comment submit(CommentDTO dto) {
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setNickname(dto.getNickname());
        comment.setEmail(dto.getEmail());
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        comment.setStatus(0); // pending
        commentMapper.insert(comment);
        return comment;
    }

    @Override
    public IPage<Comment> pageAdmin(int pageNum, int pageSize, Integer status) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        return commentMapper.selectCommentPage(page, status);
    }

    @Override
    public Comment updateStatus(Long id, Integer status) {
        Comment existing = commentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "评论不存在");
        }
        existing.setStatus(status);
        commentMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Comment existing = commentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "评论不存在");
        }
        commentMapper.deleteById(id);
    }

    /**
     * Build a nested comment tree from a flat list using parentId.
     */
    private List<Comment> buildTree(List<Comment> flat) {
        Map<Long, List<Comment>> childrenMap = flat.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Comment> roots = new ArrayList<>();
        for (Comment c : flat) {
            c.setChildren(childrenMap.getOrDefault(c.getId(), new ArrayList<>()));
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(c);
            }
        }
        return roots;
    }
}
