package com.devlog.vo;

import com.devlog.entity.Article;

/**
 * 上一篇 / 下一篇。内部只填了 id / title / slug，前端也只用这三个字段。
 */
public record NeighboursVO(Article prev, Article next) {
}
