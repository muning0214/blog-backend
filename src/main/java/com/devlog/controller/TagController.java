package com.devlog.controller;

import com.devlog.common.Result;
import com.devlog.entity.Tag;
import com.devlog.service.TagService;
import com.devlog.vo.TagCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的标签读取接口，匿名可访问。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 全部标签（不区分是否有文章） */
    @GetMapping
    public Result<List<Tag>> list() {
        return Result.ok(tagService.listAll());
    }

    /** 标签 + 已发布文章数，供标签云与统计表使用 */
    @GetMapping("/counts")
    public Result<List<TagCountVO>> counts() {
        return Result.ok(tagService.counts());
    }
}
