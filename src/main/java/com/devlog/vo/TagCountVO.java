package com.devlog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签统计行。
 *
 * <p>用 class + Lombok 而不是 record：这个类型由 MyBatis 直接做结果映射，
 * 走的是 setter 赋值，record 的构造器映射在列顺序与字段顺序不一致时容易出错。
 * 序列化成 JSON 后是 {tag, article_count, latest_at}，与前端契约一致。
 */
@Data
public class TagCountVO {

    /** 标签 slug */
    private String tag;

    private Long articleCount;

    private LocalDateTime latestAt;
}
