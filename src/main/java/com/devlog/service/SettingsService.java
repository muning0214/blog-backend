package com.devlog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devlog.common.exception.BusinessException;
import com.devlog.dto.SettingsSaveDTO;
import com.devlog.entity.SiteSettings;
import com.devlog.mapper.SiteSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站点配置服务（单行表）。
 *
 * <p>GET 是公开的：关于页、页头页脚都要读它，而这些页面匿名访客也能看。
 * PUT 需要登录，且只允许最初创建这一行的账号修改，避免多账号互相覆盖。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SiteSettingsMapper settingsMapper;

    /** 可能返回 null：数据库里还没有配置行时，前端用内置默认值兜底 */
    public SiteSettings get() {
        List<SiteSettings> rows = settingsMapper.selectList(
                Wrappers.<SiteSettings>lambdaQuery().orderByAsc(SiteSettings::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteSettings save(SettingsSaveDTO dto, Long userId) {
        SiteSettings current = get();

        if (current == null) {
            SiteSettings created = new SiteSettings();
            created.setUserId(userId);
            apply(created, dto);
            settingsMapper.insert(created);
            return created;
        }

        boolean manageable = current.getUserId() == null
                || current.getUserId() == 0L
                || current.getUserId().equals(userId);
        if (!manageable) {
            throw BusinessException.forbidden("站点配置由其他账号维护，你没有修改权限");
        }

        apply(current, dto);
        current.setUserId(userId);
        settingsMapper.updateById(current);
        return current;
    }

    /** 只覆盖传了值的字段，null 表示「这次不改这一项」 */
    private void apply(SiteSettings target, SettingsSaveDTO dto) {
        if (dto.siteName() != null) {
            target.setSiteName(dto.siteName().trim());
        }
        if (dto.tagline() != null) {
            target.setTagline(dto.tagline().trim());
        }
        if (dto.authorName() != null) {
            target.setAuthorName(dto.authorName().trim());
        }
        if (dto.authorBio() != null) {
            target.setAuthorBio(dto.authorBio().trim());
        }
        if (dto.aboutMd() != null) {
            target.setAboutMd(dto.aboutMd());
        }
        if (dto.email() != null) {
            target.setEmail(dto.email().trim());
        }
        if (dto.github() != null) {
            target.setGithub(dto.github().trim());
        }
    }
}
