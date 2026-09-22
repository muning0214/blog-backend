package com.devlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devlog.entity.Tag;
import com.devlog.vo.TagCountVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签数据访问。
 */
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 标签 + 该标签下的已发布文章数。
     *
     * <p>聚合放在数据库里做，而不是把全部文章拉到前端去数 ——
     * 后者在文章变多之后会变成明显的性能问题。
     */
    List<TagCountVO> selectTagCounts();

    /** 按 slug 列表批量取标签，用于把文章里的标签补充成完整信息 */
    List<Tag> selectBySlugs(@Param("slugs") List<String> slugs);
}
