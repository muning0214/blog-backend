package com.devlog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devlog.common.PageResult;
import com.devlog.common.exception.BusinessException;
import com.devlog.common.util.TextUtils;
import com.devlog.config.DevLogProperties;
import com.devlog.dto.ArticleQuery;
import com.devlog.dto.ArticleSaveDTO;
import com.devlog.entity.Article;
import com.devlog.entity.ArticleAttachment;
import com.devlog.entity.ArticleTag;
import com.devlog.entity.Tag;
import com.devlog.mapper.ArticleAttachmentMapper;
import com.devlog.mapper.ArticleMapper;
import com.devlog.mapper.ArticleTagMapper;
import com.devlog.mapper.TagMapper;
import com.devlog.vo.NeighboursVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章服务。
 *
 * <p>几个刻意的取舍，都对应原 test1 或云端版本里的一个具体问题：
 *
 * <ul>
 *   <li><b>标签用两次类型化查询而不是 GROUP_CONCAT</b>：先把整页文章的关联行一次查出来，
 *       再按 tag_id 批量查标签。既没有 N+1，也不会出现「标签名里带逗号导致 split 出错」。</li>
 *   <li><b>正文只在详情查询里取</b>：content 是 LONGTEXT，列表页带上它纯属浪费。</li>
 *   <li><b>写操作后校验归属</b>：先加载再比对 user_id，不是只在 SQL 里加条件，
 *       这样能给出明确的 403 而不是静默的「改了 0 行」。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleAttachmentMapper attachmentMapper;
    private final TagMapper tagMapper;
    private final TagService tagService;
    private final StorageService storageService;
    private final DevLogProperties properties;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 公开列表：强制只返回已发布，且不接受前端传入的 authorId 与 status */
    public PageResult<Article> pagePublished(ArticleQuery query) {
        query.setStatus(STATUS_PUBLISHED);
        query.setAuthorId(null);
        return page(query);
    }

    /** 作者列表：只看自己的，可按草稿/已发布筛选 */
    public PageResult<Article> pageForAuthor(Long authorId, ArticleQuery query) {
        query.setAuthorId(authorId);
        return page(query);
    }

    private PageResult<Article> page(ArticleQuery query) {
        Page<Article> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        IPage<Article> result = articleMapper.selectArticlePage(page, query);
        List<Article> rows = result.getRecords();
        enrich(rows);
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 访客读详情：草稿对访客不可见。
     *
     * @param countView 是否计入阅读量。只有真正「打开详情页」那一次才该加；
     *                  同一篇文章的 related / neighbours 派生请求必须传 false，
     *                  否则打开一篇文章会把阅读量加好几次。
     */
    public Article readPublished(String slug, boolean countView) {
        Article article = articleMapper.selectDetailBySlug(slug);
        if (article == null || !STATUS_PUBLISHED.equals(article.getStatus())) {
            throw BusinessException.notFound("文章不存在或尚未发布");
        }
        if (countView) {
            articleMapper.incrementViews(article.getId());
            article.setViews((article.getViews() == null ? 0 : article.getViews()) + 1);
        }
        enrich(List.of(article));
        return article;
    }

    /** 作者读自己的文章（含草稿），需要归属校验 */
    public Article getEditable(Long id, Long authorId) {
        Article article = requireOwned(id, authorId);
        enrich(List.of(article));
        return article;
    }

    public List<Article> related(Article article, int limit) {
        if (article.getTags() == null || article.getTags().isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<Article> rows = articleMapper.selectRelated(
                article.getId(), article.getTags().get(0), safeLimit);
        enrich(rows);
        return rows;
    }

    public NeighboursVO neighbours(Article article) {
        LocalDateTime anchor = article.getPublishedAt() != null
                ? article.getPublishedAt()
                : article.getCreatedAt();
        if (anchor == null) {
            return new NeighboursVO(null, null);
        }
        return new NeighboursVO(
                articleMapper.selectPrevNeighbour(anchor, article.getId()),
                articleMapper.selectNextNeighbour(anchor, article.getId()));
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public Article create(Long authorId, ArticleSaveDTO dto) {
        String status = normalizeStatus(dto.status(), STATUS_DRAFT);

        Article article = new Article();
        article.setUserId(authorId);
        article.setTitle(dto.title().trim());
        article.setSlug(uniqueSlug(dto.slug(), dto.title(), null));
        article.setSummary(resolveSummary(dto.summary(), dto.content()));
        article.setContent(dto.content());
        article.setCoverPath(trimToNull(dto.coverPath()));
        article.setStatus(status);
        article.setFeatured(Boolean.TRUE.equals(dto.featured()));
        article.setViews(0);
        article.setReadingMinutes(TextUtils.estimateReadingMinutes(dto.content()));
        article.setPublishedAt(STATUS_PUBLISHED.equals(status) ? LocalDateTime.now() : null);

        insertWithSlugRetry(article, dto.slug());
        replaceTags(article.getId(), dto.tags(), authorId);
        replaceAttachments(article.getId(), dto.attachments());
        log.info("文章已创建: id={}, slug={}, status={}", article.getId(), article.getSlug(), status);
        return detailForAuthor(article.getId(), authorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Article update(Long id, Long authorId, ArticleSaveDTO dto) {
        Article existing = requireOwned(id, authorId);

        Article patch = new Article();
        patch.setId(existing.getId());
        patch.setTitle(dto.title().trim());
        patch.setSlug(uniqueSlug(dto.slug(), dto.title(), existing.getId()));
        patch.setSummary(resolveSummary(dto.summary(), dto.content()));
        patch.setContent(dto.content());
        patch.setCoverPath(trimToNull(dto.coverPath()));
        patch.setReadingMinutes(TextUtils.estimateReadingMinutes(dto.content()));

        String status = dto.status() == null || dto.status().isBlank()
                ? existing.getStatus()
                : normalizeStatus(dto.status(), existing.getStatus());
        patch.setStatus(status);
        // 首次从草稿变为已发布时记录发布时间；之后再改状态不覆盖原始发布时间
        if (STATUS_PUBLISHED.equals(status) && existing.getPublishedAt() == null) {
            patch.setPublishedAt(LocalDateTime.now());
        }
        if (dto.featured() != null) {
            patch.setFeatured(dto.featured());
        }

        articleMapper.updateById(patch);
        replaceTags(existing.getId(), dto.tags(), authorId);
        replaceAttachments(existing.getId(), dto.attachments());
        return detailForAuthor(existing.getId(), authorId);
    }

    /**
     * 删除文章。
     *
     * <p>顺序很重要：先把要清理的文件路径收集出来，再删数据库行 ——
     * 反过来的话附件记录已经没了，文件就会永远留在磁盘上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long authorId) {
        Article article = requireOwned(id, authorId);
        List<String> assets = collectAssetPaths(article);

        attachmentMapper.delete(Wrappers.<ArticleAttachment>lambdaQuery()
                .eq(ArticleAttachment::getArticleId, id));
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, id));

        // 逻辑删除前先把 slug 改掉：唯一索引不认 deleted 标记，
        // 不释放的话「删掉旧文章再用同样的 slug 建新文章」会撞唯一键。
        Article slugPatch = new Article();
        slugPatch.setId(id);
        slugPatch.setSlug(deletedSlug(article.getSlug(), id));
        articleMapper.updateById(slugPatch);
        articleMapper.deleteById(id);

        storageService.deleteAll(assets);
        log.info("文章已删除: id={}, 同时清理文件 {} 个", id, assets.size());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private Article requireOwned(Long id, Long authorId) {
        Article article = articleMapper.selectDetailById(id);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        if (!Objects.equals(article.getUserId(), authorId)) {
            throw BusinessException.forbidden("这不是你的文章，无法操作");
        }
        return article;
    }

    private Article detailForAuthor(Long id, Long authorId) {
        Article article = articleMapper.selectDetailById(id);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        enrich(List.of(article));
        return article;
    }

    /**
     * 插入并对 slug 唯一冲突做一次退避重试。
     *
     * <p>唯一索引是最终的权威判定 —— 先查再插在并发下仍可能撞车，
     * 所以这里兜一层，把 23505 变成一次自动换 slug 重试，而不是把异常抛给用户。
     */
    private void insertWithSlugRetry(Article article, String requestedSlug) {
        try {
            articleMapper.insert(article);
        } catch (DuplicateKeyException e) {
            String base = requestedSlug == null || requestedSlug.isBlank()
                    ? article.getSlug() : TextUtils.slugify(requestedSlug);
            for (int i = 0; i < 5; i++) {
                article.setId(null);
                article.setSlug(base + "-" + randomSuffix(6));
                try {
                    articleMapper.insert(article);
                    return;
                } catch (DuplicateKeyException ignored) {
                    // 继续重试
                }
            }
            throw BusinessException.conflict("链接标识重复次数过多，请手动换一个");
        }
    }

    private String uniqueSlug(String requested, String title, Long excludeId) {
        String base = (requested == null || requested.isBlank())
                ? TextUtils.slugify(title)
                : TextUtils.slugify(requested);
        String candidate = base;
        for (int i = 0; i < 5; i++) {
            Long exists = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                    .eq(Article::getSlug, candidate)
                    .ne(excludeId != null, Article::getId, excludeId));
            if (exists == null || exists == 0) {
                return candidate;
            }
            candidate = base + "-" + randomSuffix(4);
        }
        return base + "-" + randomSuffix(8);
    }

    /** 释放 slug：附加 id 保证唯一，同时把长度控制在列宽内 */
    private String deletedSlug(String slug, Long id) {
        String suffix = "-deleted-" + id;
        String base = slug == null ? "post" : slug;
        int room = 160 - suffix.length();
        if (base.length() > room) {
            base = base.substring(0, Math.max(1, room));
        }
        return base + suffix;
    }

    private void replaceTags(Long articleId, List<String> slugs, Long authorId) {
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, articleId));
        List<Long> tagIds = tagService.resolveTagIds(slugs, authorId);
        if (!tagIds.isEmpty()) {
            articleTagMapper.batchInsert(articleId, tagIds);
        }
    }

    private void replaceAttachments(Long articleId, List<ArticleSaveDTO.AttachmentInput> inputs) {
        attachmentMapper.delete(Wrappers.<ArticleAttachment>lambdaQuery()
                .eq(ArticleAttachment::getArticleId, articleId));
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        int sort = 0;
        for (ArticleSaveDTO.AttachmentInput input : inputs) {
            ArticleAttachment attachment = new ArticleAttachment();
            attachment.setArticleId(articleId);
            attachment.setName(input.name().trim());
            attachment.setPath(input.path().trim());
            attachment.setSizeBytes(input.size() == null ? 0L : input.size());
            attachment.setMime(input.mime() == null ? "" : input.mime().trim());
            attachment.setSort(sort++);
            attachmentMapper.insert(attachment);
        }
    }

    /** 给整页文章补上标签与附件，两次批量查询覆盖全部行，没有 N+1 */
    private void enrich(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        List<Long> ids = articles.stream()
                .map(Article::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }

        List<ArticleTag> links = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().in(ArticleTag::getArticleId, ids));
        Map<Long, List<Long>> tagIdsByArticle = new HashMap<>();
        Set<Long> allTagIds = new LinkedHashSet<>();
        for (ArticleTag link : links) {
            tagIdsByArticle.computeIfAbsent(link.getArticleId(), k -> new ArrayList<>()).add(link.getTagId());
            allTagIds.add(link.getTagId());
        }

        Map<Long, String> slugByTagId = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            for (Tag tag : tagMapper.selectBatchIds(allTagIds)) {
                slugByTagId.put(tag.getId(), tag.getSlug());
            }
        }

        List<ArticleAttachment> attachments = attachmentMapper.selectList(
                Wrappers.<ArticleAttachment>lambdaQuery()
                        .in(ArticleAttachment::getArticleId, ids)
                        .orderByAsc(ArticleAttachment::getSort)
                        .orderByAsc(ArticleAttachment::getId));
        Map<Long, List<Article.Attachment>> attachmentsByArticle = new HashMap<>();
        for (ArticleAttachment row : attachments) {
            attachmentsByArticle
                    .computeIfAbsent(row.getArticleId(), k -> new ArrayList<>())
                    .add(new Article.Attachment(row.getName(), row.getPath(), row.getSizeBytes(), row.getMime()));
        }

        for (Article article : articles) {
            List<Long> tagIds = tagIdsByArticle.getOrDefault(article.getId(), List.of());
            article.setTags(tagIds.stream()
                    .map(slugByTagId::get)
                    .filter(Objects::nonNull)
                    .toList());
            article.setAttachments(attachmentsByArticle.getOrDefault(article.getId(), List.of()));
        }
    }

    /** 封面 + 附件 + 正文里引用到的 /uploads 路径 */
    private List<String> collectAssetPaths(Article article) {
        Set<String> paths = new LinkedHashSet<>();
        if (article.getCoverPath() != null && !article.getCoverPath().isBlank()) {
            paths.add(article.getCoverPath());
        }
        for (Article.Attachment attachment : article.getAttachments()) {
            if (attachment.path() != null && !attachment.path().isBlank()) {
                paths.add(attachment.path());
            }
        }
        String content = article.getContent();
        if (content != null && !content.isBlank()) {
            String prefix = properties.upload().publicPrefixOrDefault();
            Pattern pattern = Pattern.compile(
                    Pattern.quote(prefix) + "/((?:covers|images|avatars|attachments)/[A-Za-z0-9._-]+)");
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                paths.add(matcher.group(1));
            }
        }
        return new ArrayList<>(paths);
    }

    private String normalizeStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase();
        if (!STATUS_DRAFT.equals(value) && !STATUS_PUBLISHED.equals(value)) {
            throw BusinessException.badRequest("文章状态只能是 draft 或 published");
        }
        return value;
    }

    private String resolveSummary(String provided, String content) {
        if (provided != null && !provided.isBlank()) {
            return TextUtils.truncate(provided.trim(), 500);
        }
        return TextUtils.truncate(TextUtils.summarize(content, 120), 500);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
