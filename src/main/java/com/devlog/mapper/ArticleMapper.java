package com.devlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devlog.dto.ArticleQuery;
import com.devlog.entity.Article;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章数据访问。
 *
 * <p>分页方法的第一位参数是 {@code Page}，配合已注册的分页插件自动注入 LIMIT 与 COUNT。
 * 列表查询刻意不选 content 列 —— 正文是 LONGTEXT，列表页并不需要它，
 * 带上是纯粹的浪费。需要正文的场景走 selectDetail*。
 */
public interface ArticleMapper extends BaseMapper<Article> {

    IPage<Article> selectArticlePage(Page<Article> page, @Param("q") ArticleQuery query);

    /** 按 slug 取详情（含正文） */
    Article selectDetailBySlug(@Param("slug") String slug);

    /** 按主键取详情（含正文） */
    Article selectDetailById(@Param("id") Long id);

    /** 上一篇：比锚点更早的一篇 */
    Article selectPrevNeighbour(@Param("anchor") LocalDateTime anchor, @Param("selfId") Long selfId);

    /** 下一篇：比锚点更新的一篇 */
    Article selectNextNeighbour(@Param("anchor") LocalDateTime anchor, @Param("selfId") Long selfId);

    /** 同标签的其它已发布文章 */
    List<Article> selectRelated(@Param("selfId") Long selfId,
                                @Param("tagSlug") String tagSlug,
                                @Param("limit") int limit);

    /**
     * 阅读量原子自增。
     *
     * <p>用 {@code views = views + 1} 而不是「读出来加一再写回」，
     * 后者在并发下会丢计数。同时限定 status 与 deleted，
     * 保证草稿和已删除文章不会被外部触发计数。
     */
    int incrementViews(@Param("id") Long id);
}
