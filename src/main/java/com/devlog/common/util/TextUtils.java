package com.devlog.common.util;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 文本处理工具：slug 生成、阅读时长估算、Markdown 摘要提取。
 */
public final class TextUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** 连续的非字母数字（含中文、标点、空白）会被折叠成一个连字符 */
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final Pattern TAG_ILLEGAL_CHARS = Pattern.compile("[#?&/=+%\\\\\\s]+");
    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern MD_LINK = Pattern.compile("!?\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern MD_MARKS = Pattern.compile("[*_>#~|]");
    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u4dbf\\u4e00-\\u9fff]");
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z0-9_'’-]+");

    private TextUtils() {
    }

    /**
     * 由标题推导 URL slug。
     *
     * <p>中文标题无法转成有意义的 ASCII slug，这时回退成 {@code post-xxxxxx}。
     * 编辑器里永远允许手工改写，所以这只是个初始值，不必强求可读。
     */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return randomSlug();
        }
        String ascii = EDGE_HYPHENS.matcher(
                NON_SLUG_CHARS.matcher(input.trim().toLowerCase()).replaceAll("-")).replaceAll("");
        if (ascii.replace("-", "").length() < 2) {
            return randomSlug();
        }
        return ascii.length() > 64 ? ascii.substring(0, 64).replaceAll("-+$", "") : ascii;
    }

    /** 标签标识：保留中文（标签不进 URL 路径，只做查询条件），去掉会破坏查询的字符 */
    public static String tagSlug(String input) {
        if (input == null || input.isBlank()) {
            return "tag";
        }
        String slug = TAG_ILLEGAL_CHARS.matcher(input.trim().toLowerCase()).replaceAll("").trim();
        return slug.isEmpty() ? "tag" : (slug.length() > 60 ? slug.substring(0, 60) : slug);
    }

    /**
     * 估算阅读时长：中文按 350 字/分钟，英文按 200 词/分钟，不足一分钟记一分钟。
     * 数值存在文章行上，避免每次列表查询都重新算一遍。
     */
    public static int estimateReadingMinutes(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 1;
        }
        String body = CODE_FENCE.matcher(markdown).replaceAll(" ");
        int cjk = countMatches(CJK, body);
        int words = countMatches(LATIN_WORD, body);
        double minutes = cjk / 350.0 + words / 200.0;
        return Math.max(1, (int) Math.round(minutes));
    }

    /**
     * 由正文提取摘要：去掉代码块、行内代码、链接与 Markdown 标记，
     * 只留纯文本。作者没填摘要时用它兜底，避免列表页出现大片空白。
     */
    public static String summarize(String markdown, int maxChars) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = CODE_FENCE.matcher(markdown).replaceAll(" ");
        text = INLINE_CODE.matcher(text).replaceAll(" ");
        text = MD_LINK.matcher(text).replaceAll(" ");
        text = MD_MARKS.matcher(text).replaceAll(" ");
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars).trim() + "…";
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String randomSlug() {
        StringBuilder sb = new StringBuilder("post-");
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM_ALPHABET.charAt(RANDOM.nextInt(RANDOM_ALPHABET.length())));
        }
        return sb.toString();
    }
}
