package com.devlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devlog.entity.ArticleTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章-标签关联的数据访问。
 *
 * <p>按文章批量读标签不走这里 —— 那是「先按 article_id 批量查出 ArticleTag 行，
 * 再按 tag_id 批量查 Tag」两次查询，全部用 MyBatis-Plus 的类型化 API 完成，
 * 既避免了 N+1，也避免了把结果塞进 Map 带来的键名不确定性。
 */
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * 批量插入关联，避免逐个 insert 造成 N 次往返。
     * 单条 SQL 完成，冲突时忽略（处理并发下的重复关联）。
     */
    int batchInsert(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);
}
