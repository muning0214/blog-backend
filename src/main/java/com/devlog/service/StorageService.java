package com.devlog.service;

import com.devlog.common.exception.BusinessException;
import com.devlog.config.DevLogProperties;
import com.devlog.vo.UploadVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储。
 *
 * <p>对应原云端版本里的对象存储模块。当时文件放在云端，读权限只授予登录用户，
 * 于是匿名访客看不到博客图片，只能显示占位。现在文件在自己的磁盘上，
 * 通过 WebMvcConfig 的静态资源映射整体对外开放，图片 URL 稳定可缓存。
 *
 * <p>安全上有三道防线：
 * <ol>
 *   <li><b>文件名不用用户提供的</b>：落盘名是 UUID + 白名单扩展名，
 *       从根上避免路径穿越与重名覆盖。</li>
 *   <li><b>写盘前再校验一次归一化路径</b>，确认目标仍在 uploads 根目录内。</li>
 *   <li><b>附件拒绝可执行 / 可脚本化的类型</b>。这一步很关键：文件是由同源的
 *       /uploads/** 静态资源直接返回的，如果允许上传 .html 或 .svg，
 *       攻击者就能得到一个同源页面，等于存储型 XSS。</li>
 * </ol>
 */
@Slf4j
@Service
public class StorageService {

    /** 允许的目录，防止调用方传任意前缀 */
    private static final Set<String> ALLOWED_FOLDERS = Set.of("covers", "images", "attachments", "avatars");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "avif");

    /**
     * 附件黑名单。
     * 这里用黑名单而不是白名单，是因为附件本来就该允许各种文档格式（zip、pdf、docx…），
     * 逐个枚举不现实；而真正危险的是「浏览器会当代码执行」的那几类。
     */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "html", "htm", "xhtml", "shtml", "svg", "xml", "xsl", "xslt",
            "js", "mjs", "cjs", "jsx", "ts", "map",
            "jsp", "jspx", "php", "asp", "aspx", "cgi",
            "exe", "dll", "bat", "cmd", "com", "msi", "scr", "ps1", "sh", "jar", "war");

    /** 同上，按声明的 Content-Type 再挡一道 */
    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml",
            "application/javascript", "text/javascript", "application/x-javascript");

    private final DevLogProperties properties;
    private final Path root;

    public StorageService(DevLogProperties properties) throws IOException {
        this.properties = properties;
        this.root = Paths.get(properties.upload().dirOrDefault()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("上传目录已就绪: {}", root);
    }

    /**
     * 保存上传文件。
     *
     * @param folder covers / images / attachments / avatars
     */
    public UploadVO store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("没有收到文件内容");
        }
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw BusinessException.badRequest("不支持的上传目录：" + folder);
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        boolean imageFolder = "covers".equals(folder) || "images".equals(folder) || "avatars".equals(folder);

        if (imageFolder) {
            if (!IMAGE_EXTENSIONS.contains(extension) || (contentType.startsWith("image/") && isBlockedContentType(contentType))) {
                throw BusinessException.badRequest("封面与插图只支持 png / jpg / jpeg / webp / gif / avif");
            }
            if (file.getSize() > properties.upload().maxImageBytesOrDefault()) {
                throw BusinessException.badRequest("图片超出大小限制：" + humanSize(properties.upload().maxImageBytesOrDefault()));
            }
        } else {
            if (BLOCKED_EXTENSIONS.contains(extension) || isBlockedContentType(contentType)) {
                throw BusinessException.badRequest("出于安全考虑，不支持上传该类型的附件：" + extension);
            }
            if (file.getSize() > properties.upload().maxFileBytesOrDefault()) {
                throw BusinessException.badRequest("附件超出大小限制：" + humanSize(properties.upload().maxFileBytesOrDefault()));
            }
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + (extension.isEmpty() ? "" : "." + extension);
        String relative = folder + "/" + storedName;

        Path dir = root.resolve(folder).normalize();
        Path target = dir.resolve(storedName).normalize();
        // 双保险：即便上面哪一步出问题，也不允许写到 uploads 根目录之外
        if (!target.startsWith(root) || !target.startsWith(dir)) {
            throw BusinessException.badRequest("非法的文件目标路径");
        }

        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("文件写入失败: {}", target, e);
            throw new BusinessException(500, "文件保存失败，请稍后重试");
        }

        return new UploadVO(originalName, relative, publicUrl(relative), file.getSize(), file.getContentType());
    }

    /**
     * 删除一个对象路径。路径来自数据库，但仍然要校验：
     * 数据被篡改或早期脏数据都可能带出 uploads 之外的路径。
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.contains("\\")) {
            log.warn("跳过可疑的文件路径: {}", relativePath);
            return;
        }
        String folder = relativePath.contains("/")
                ? relativePath.substring(0, relativePath.indexOf('/'))
                : "";
        if (!ALLOWED_FOLDERS.contains(folder)) {
            log.warn("跳过不在白名单目录下的文件: {}", relativePath);
            return;
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("跳过越界的文件路径: {}", relativePath);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 删文件失败不应该让主流程失败（例如文章该删还是要删）
            log.warn("文件删除失败: {}", target, e);
        }
    }

    public void deleteAll(List<String> relativePaths) {
        if (relativePaths == null) {
            return;
        }
        relativePaths.forEach(this::delete);
    }

    /** 拼出对外的访问地址 */
    public String publicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String prefix = properties.upload().publicPrefixOrDefault();
        if (relativePath.startsWith(prefix + "/")) {
            return relativePath;
        }
        return prefix + "/" + relativePath;
    }

    private boolean isBlockedContentType(String contentType) {
        return BLOCKED_CONTENT_TYPES.contains(contentType);
    }

    /** 原始文件名只用于展示，这里去掉路径成分与控制字符 */
    private String sanitizeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return "unnamed";
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return (bytes / 1024 / 1024) + " MB";
        }
        return (bytes / 1024) + " KB";
    }
}
