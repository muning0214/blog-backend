package com.devlog.controller;

import com.devlog.common.Result;
import com.devlog.common.exception.BusinessException;
import com.devlog.service.StorageService;
import com.devlog.vo.UploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传 / 删除。同样位于拦截器保护的 /api/author/** 下，未登录不能上传。
 *
 * <p>目录通过 folder 参数选择，但只接受白名单里的值（在 StorageService 里校验），
 * 不是任意路径。
 */
@RestController
@RequestMapping("/api/author/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    @PostMapping
    public Result<UploadVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "images") String folder) {
        return Result.ok(storageService.store(file, folder));
    }

    /**
     * 删除一个已上传的文件。
     *
     * <p>传入的是对象路径（如 images/ab12.png），不是完整 URL。
     * StorageService 会再次校验路径是否落在 uploads 之内。
     */
    @DeleteMapping
    public Result<Void> delete(@RequestParam("path") String path) {
        if (path == null || path.isBlank()) {
            throw BusinessException.badRequest("缺少要删除的文件路径");
        }
        storageService.delete(path);
        return Result.ok("已删除", null);
    }
}
