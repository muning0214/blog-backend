package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.entity.Tag;
import com.blog.mapper.TagMapper;
import com.blog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Tag service implementation.
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<Map<String, Object>> listWithCount() {
        return tagMapper.selectTagsWithCount();
    }

    @Override
    public List<Tag> listAll() {
        LambdaQueryWrapper<Tag> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(Tag::getId);
        return tagMapper.selectList(qw);
    }

    @Override
    public Tag create(Tag tag) {
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            throw new BusinessException(400, "标签名不能为空");
        }
        // Check duplicate name
        LambdaQueryWrapper<Tag> qw = new LambdaQueryWrapper<>();
        qw.eq(Tag::getName, tag.getName().trim());
        if (tagMapper.selectCount(qw) > 0) {
            throw new BusinessException(400, "标签名已存在");
        }
        tag.setName(tag.getName().trim());
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    public Tag update(Long id, Tag tag) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "标签不存在");
        }
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            throw new BusinessException(400, "标签名不能为空");
        }
        existing.setName(tag.getName().trim());
        tagMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "标签不存在");
        }
        tagMapper.deleteById(id);
    }
}
