package com.hsmty.cmr.file.controller;

import com.hsmty.cmr.file.service.FileObjectService;
import com.hsmty.cmr.file.vo.FileContent;
import com.hsmty.cmr.pojo.dto.FileObjectListDTO;
import com.hsmty.cmr.pojo.vo.FileObjectVO;
import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.core.common.entity.PageResult;
import com.hsmty.core.common.entity.SelectIdsDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 文件对象接口。
 *
 * <p>只做参数接收 → 调 Service → 包装返回；文件本体通过有界流式响应输出。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "文件对象")
@RestController
@RequestMapping("files")
@RequiredArgsConstructor
public class FileObjectController {

    private final FileObjectService fileObjectService;

    @Operation(summary = "上传文件")
    @PreAuthorize("hasAnyAuthority('cmr:file:upload')")
    @PostMapping
    public ApiResult<FileObjectVO> upload(@RequestPart("file") MultipartFile file,
                                          @RequestParam(value = "bizType", required = false) String bizType) {
        return ApiResult.success(fileObjectService.upload(file, bizType));
    }

    @Operation(summary = "分页查询文件")
    @PreAuthorize("hasAnyAuthority('cmr:file:page')")
    @GetMapping
    public ApiResult<PageResult<FileObjectVO>> page(FileObjectListDTO dto) {
        return ApiPageResult.success(fileObjectService.page(dto));
    }

    @Operation(summary = "文件详情")
    @PreAuthorize("hasAnyAuthority('cmr:file:detail')")
    @GetMapping("{id}")
    public ApiResult<FileObjectVO> detail(@PathVariable Long id) {
        return ApiResult.success(fileObjectService.detail(id));
    }

    @Operation(summary = "批量删除文件")
    @PreAuthorize("hasAnyAuthority('cmr:file:remove')")
    @DeleteMapping
    public ApiResult<Void> remove(@Valid @RequestBody SelectIdsDTO dto) {
        fileObjectService.remove(dto);
        return ApiResult.success();
    }

    @Operation(summary = "预览文件内容（inline，不提供下载）")
    @PreAuthorize("hasAnyAuthority('cmr:file:read')")
    @GetMapping("{id}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable Long id) {
        FileContent content = fileObjectService.content(id);
        MediaType mediaType = StringUtils.hasText(content.contentType())
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = content.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }

    @Operation(summary = "根据 ID 批量查询文件元数据")
    @PreAuthorize("hasAnyAuthority('cmr:file:read')")
    @PostMapping("by-ids")
    public ApiResult<List<FileObjectVO>> byIds(@RequestBody Collection<Long> ids) {
        return ApiResult.success(fileObjectService.getByIdList(ids));
    }
}
