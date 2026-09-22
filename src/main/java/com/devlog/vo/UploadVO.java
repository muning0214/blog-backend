package com.devlog.vo;

/**
 * 上传成功后的返回体。
 *
 * <p>path 是要存进数据库的对象键（相对 uploads 根目录），url 是可直接访问的地址。
 * 数据库只存 path 不存 url：将来换域名或加 CDN 时，老数据不用改。
 */
public record UploadVO(String name, String path, String url, Long size, String mime) {
}
