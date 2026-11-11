package com.hsmty.cmr.common.exception;

import com.hsmty.core.common.entity.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 旧版业务异常的统一下发。
 *
 * <p>平台 {@code GlobalExceptionHandler} 只识别平台的 {@code BusinessException}，
 * 旧版业务代码统一抛 {@link BizException}，若不单独接管会被当成未知异常返回
 * {@code C9999}，丢失可读的失败原因。这里按平台口径补一个更具体的处理器：
 * 业务规则失败返回 {@code C1008}，上传超限返回 {@code C1005}。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BizExceptionHandler {

    /** 业务规则不满足（对应平台 CommonResponseEnum.INVALID） */
    private static final String CODE_INVALID = "C1008";

    /** 上传文件超限（对应平台 CommonResponseEnum.FILE_UPLOAD_SIZE_ERROR） */
    private static final String CODE_UPLOAD_SIZE = "C1005";

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResult.error(CODE_INVALID, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return ApiResult.error(CODE_UPLOAD_SIZE, "上传文件超出大小限制");
    }
}
