package com.devlog.common;

import java.util.List;

/**
 * 分页响应。字段名会按 SNAKE_CASE 序列化成 list / total / page / size / total_pages。
 */
public record PageResult<T>(List<T> list, long total, long page, long size, long totalPages) {

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        long safeSize = size <= 0 ? 1 : size;
        long pages = (total + safeSize - 1) / safeSize;
        return new PageResult<>(list, total, page, safeSize, pages);
    }
}
