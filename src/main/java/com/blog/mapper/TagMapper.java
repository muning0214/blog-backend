package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Tag data access layer.
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * Returns all tags with the count of published articles linked to each.
     */
    List<Map<String, Object>> selectTagsWithCount();

    /**
     * Returns tag ids linked to a given article.
     */
    List<Long> selectTagIdsByArticleId(@Param("articleId") Long articleId);
}
