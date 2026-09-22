package com.devlog.common.exception;

import com.devlog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>这里有两个刻意的设计，都是为了修掉原 test1 项目里的两个真实缺陷：
 *
 * <ol>
 *   <li><b>返回 ResponseEntity 而不是裸的 Result</b>。
 *       如果只 return Result，业务异常会以 HTTP 200 送出去，前端若按
 *       {@code error.response.status === 401} 判断登录失效就永远不会命中，
 *       结果是 token 过期后用户卡在后台、既不跳登录也不清凭据，只看到一堆 toast。</li>
 *   <li><b>500 不把异常信息回给客户端</b>。
 *       原实现返回 {@code "服务器内部错误: " + e.getMessage()}，
 *       于是 SQL 报错、文件路径、连接串都可能被泄露出去。
 *       这里只在服务端日志里留全量堆栈，对外统一一句通用文案。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (status.is5xxServerError()) {
            log.error("业务异常 {} -> {}", request.getRequestURI(), e.getCode(), e);
        } else {
            log.warn("业务异常 {} -> {} {}", request.getRequestURI(), e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请求参数不合法";
        }
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传体积超限: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "上传的文件过大，请压缩后重试"));
    }

    /** 兜底：日志里留全量信息，响应里只给通用文案，避免泄露内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("未预期的异常 {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器处理请求时出错，请稍后重试"));
    }
}
