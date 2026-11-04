package com.hsmty.cmr.file;

import com.hsmty.cmr.common.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Map;

/**
 * 共享卷文件接口：媒体预览（Range）/材料下载统一入口、查询文件上传
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "文件")
@RestController
@RequestMapping("file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "预览/下载共享卷文件（支持 Range）")
    @PreAuthorize("hasAnyAuthority('cmr:file:read')")
    @GetMapping
    public ResponseEntity<Resource> read(@RequestParam String path,
                                         @RequestParam(required = false, defaultValue = "false") boolean download,
                                         HttpServletRequest request) throws IOException {
        LogicalFile logical = fileService.resolve(path);
        long length = Files.size(logical.absolute());
        return serve(logical, length, download, request.getHeader(HttpHeaders.RANGE));
    }

    @Operation(summary = "上传查询文件（以图/音/视搜检索）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("upload")
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResult.success(Map.of("path", fileService.saveQueryFile(file)));
    }

    /** Range 单段解析与 206 响应；无 Range 返回 200 全量 */
    private ResponseEntity<Resource> serve(LogicalFile logical, long length, boolean download, String rangeHeader)
            throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(logical.mime()));
        if (download) {
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename(logical.absolute().getFileName().toString()).build());
        }
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) {
            headers.setContentLength(length);
            return new ResponseEntity<>(new FileSystemResource(logical.absolute()), headers, HttpStatus.OK);
        }

        String spec = rangeHeader.substring(6);
        long start;
        long end;
        try {
            int dash = spec.indexOf('-');
            if (dash == 0) { // bytes=-N → 最后 N 字节
                long suffix = Long.parseLong(spec.substring(1).trim());
                start = Math.max(length - suffix, 0);
                end = length - 1;
            } else if (dash == spec.length() - 1) { // bytes=N-
                start = Long.parseLong(spec.substring(0, dash).trim());
                end = length - 1;
            } else {
                start = Long.parseLong(spec.substring(0, dash).trim());
                end = Long.parseLong(spec.substring(dash + 1).trim());
            }
        } catch (NumberFormatException e) {
            headers.setContentLength(length);
            return new ResponseEntity<>(new FileSystemResource(logical.absolute()), headers, HttpStatus.OK);
        }
        if (start > end || start >= length) {
            HttpHeaders err = new HttpHeaders();
            err.set(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
            return new ResponseEntity<>(err, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        end = Math.min(end, length - 1);
        long clipLen = end - start + 1;

        headers.setContentLength(clipLen);
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
        return new ResponseEntity<>(
                new InputStreamResource(new ClipStream(logical, start, clipLen)), headers, HttpStatus.PARTIAL_CONTENT);
    }

    /** 从任意偏移读固定长度，读完/中断自动关闭底层文件 */
    private static final class ClipStream extends InputStream {

        private final RandomAccessFile raf;
        private long remaining;

        ClipStream(LogicalFile logical, long start, long len) throws IOException {
            this.raf = new RandomAccessFile(logical.absolute().toFile(), "r");
            this.raf.seek(start);
            this.remaining = len;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = raf.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = raf.read(buf, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }
}
