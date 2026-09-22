package com.devlog.dto;

import lombok.Data;

/**
 * 文章列表查询条件。
 *
 * <p>刻意用 class + Lombok 而不是 record：这个对象要参与 MyBatis XML 里的
 * {@code <if test="q.keyword != null">} 判断，OGNL 解析属性依赖 getter，
 * record 只有 {@code keyword()} 这种访问器，容易在运行时踩坑。请求体用 record 没问题，
 * 但进入 XML 的查询对象一律用带 getter 的类。
 */
@Data
public class ArticleQuery {

    /** 关键词，匹配标题 / 摘要 / 正文 */
    private String keyword;

    /** 标签 slug */
    private String tag;

    /** draft / published；为空表示不过滤（仅作者视角使用） */
    private String status;

    /** 限定作者；为空表示全站 */
    private Long authorId;

    private Boolean featuredOnly;

    private Integer page;

    private Integer size;

    public int pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return 6;
        }
        // 上限兜底：即便前端传了很大的 size 也不会把整表拉出来
        return Math.min(size, 50);
    }
}
