package com.devlog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devlog.common.exception.BusinessException;
import com.devlog.common.util.TextUtils;
import com.devlog.dto.TagSaveDTO;
import com.devlog.entity.ArticleTag;
import com.devlog.entity.Tag;
import com.devlog.mapper.ArticleTagMapper;
import com.devlog.mapper.TagMapper;
import com.devlog.vo.TagCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签服务。
 *
 * <p>标签是「写文章时顺手创建、之后单独维护」的形态：作者在编辑器里输入标签名，
 * 后端负责把 slug 解析成 id，缺失的自动补建，前端不需要先调一次「创建标签」。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private static final String[] PALETTE = {
            "#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#ef4444",
            "#8b5cf6", "#ec4899", "#06b6d4", "#84cc16", "#f97316"
    };

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    public List<Tag> listAll() {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getName));
    }

    public List<TagCountVO> counts() {
        return tagMapper.selectTagCounts();
    }

    /**
     * 把一批标签 slug 解析成 tagId，缺失的自动创建。
     *
     * @return 与输入顺序一致（已去重）的 id 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> resolveTagIds(List<String> rawSlugs, Long userId) {
        if (rawSlugs == null || rawSlugs.isEmpty()) {
            return List.of();
        }

        // 去重 + 归一化，保持输入顺序
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawSlugs) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            normalized.add(TextUtils.tagSlug(raw));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<Tag> existing = tagMapper.selectBySlugs(new ArrayList<>(normalized));
        Map<String, Long> idBySlug = new HashMap<>();
        for (Tag tag : existing) {
            idBySlug.put(tag.getSlug(), tag.getId());
        }

        List<Long> result = new ArrayList<>(normalized.size());
        for (String slug : normalized) {
            Long id = idBySlug.get(slug);
            if (id == null) {
                Tag created = new Tag();
                created.setUserId(userId);
                created.setName(slug);
                created.setSlug(slug);
                created.setColor(pickColor(slug));
                tagMapper.insert(created);
                id = created.getId();
                idBySlug.put(slug, id);
            }
            result.add(id);
        }
        return result;
    }

    public Tag create(Long userId, TagSaveDTO dto) {
        String slug = TextUtils.tagSlug(
                (dto.slug() == null || dto.slug().isBlank()) ? dto.name() : dto.slug());
        Long exists = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery().eq(Tag::getSlug, slug));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("标签标识「" + slug + "」已存在");
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(dto.name().trim());
        tag.setSlug(slug);
        tag.setDescription(dto.description());
        tag.setColor(dto.color() == null || dto.color().isBlank() ? pickColor(slug) : dto.color());
        tagMapper.insert(tag);
        return tag;
    }

    public Tag update(Long id, Long userId, TagSaveDTO dto) {
        Tag tag = requireOwned(id, userId);
        if (dto.name() != null && !dto.name().isBlank()) {
            tag.setName(dto.name().trim());
        }
        if (dto.description() != null) {
            tag.setDescription(dto.description());
        }
        if (dto.color() != null && !dto.color().isBlank()) {
            tag.setColor(dto.color());
        }
        tagMapper.updateById(tag);
        return tag;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Tag tag = requireOwned(id, userId);
        // 先清关联，否则标签统计里会残留一个 0 篇的条目
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, id));
        tagMapper.deleteById(tag.getId());
    }

    private Tag requireOwned(Long id, Long userId) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw BusinessException.notFound("标签不存在");
        }
        // userId == 0 表示系统预置标签，允许任何登录用户维护
        boolean manageable = tag.getUserId() == null
                || tag.getUserId() == 0L
                || tag.getUserId().equals(userId);
        if (!manageable) {
            throw BusinessException.forbidden("该标签由其他账号创建，你没有修改权限");
        }
        return tag;
    }

    /** 由 slug 稳定地取一个颜色，保证多次创建同名标签颜色一致 */
    private String pickColor(String seed) {
        int hash = 0;
        for (int i = 0; i < seed.length(); i++) {
            hash = (hash * 31 + seed.charAt(i)) % 9973;
        }
        return PALETTE[Math.abs(hash) % PALETTE.length];
    }
}
