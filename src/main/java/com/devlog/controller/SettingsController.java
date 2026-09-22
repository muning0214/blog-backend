package com.devlog.controller;

import com.devlog.common.Result;
import com.devlog.entity.SiteSettings;
import com.devlog.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开的站点配置读取接口。
 *
 * <p>关于页、页头页脚都依赖它，而这些页面匿名访客也要看，所以必须公开。
 * 返回的 data 允许为 null —— 数据库里还没有配置行时，前端用内置默认值兜底。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Result<SiteSettings> get() {
        return Result.ok(settingsService.get());
    }
}
