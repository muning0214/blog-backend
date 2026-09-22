package com.devlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devlog.entity.ArticleAttachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 附件数据访问。
 */
public interface ArticleAttachmentMapper extends BaseMapper<ArticleAttachment> {

    /** 批量取一组文章的附件，由 Service 按 articleId 分组，避免 N+1 */
    List<ArticleAttachment> selectByArticleIds(@Param("articleIds") List<Long> articleIds);
}
