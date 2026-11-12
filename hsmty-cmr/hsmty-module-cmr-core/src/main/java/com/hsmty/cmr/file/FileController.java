package com.hsmty.cmr.file;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.dataset.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * 共享卷文件接口：媒体预览（Range）、查询文件上传（数据不出域，不提供下载/导出）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Tag(name = "文件")
@RestController
@RequestMapping("file")
@RequiredArgsConstructor
public class FileController {

    private static final HttpClient UPSTREAM = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final FileService fileService;
    private final DatasourceService datasourceService;

    @Operation(summary = "预览共享卷文件（支持 Range，不提供下载）")
    @PreAuthorize("hasAnyAuthority('cmr:file:read')")
    @GetMapping
    public ResponseEntity<Resource> read(@RequestParam String path,
                                         HttpServletRequest request) throws IOException {
        LogicalFile logical = fileService.resolve(path);
        long length = Files.size(logical.absolute());
        return serve(logical, length, request.getHeader(HttpHeaders.RANGE));
    }

    @Operation(summary = "回源预览上游媒体（服务端代理，不提供下载）")
    @PreAuthorize("hasAnyAuthority('cmr:file:read')")
    @GetMapping("source")
    public ResponseEntity<Resource> source(@RequestParam String url, HttpServletRequest request)
            throws IOException, InterruptedException {
        if (!datasourceService.isTrustedMediaUrl(url)) {
            return upstreamError(HttpStatus.FORBIDDEN, "仅允许回源已登记数据源的媒体地址");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return upstreamError(HttpStatus.BAD_REQUEST, "媒体地址不合法");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT, "*/*")
                .GET();
        String authorization = datasourceService.authHeaderFor(url);
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        String range = request.getHeader(HttpHeaders.RANGE);
        if (range != null) {
            builder.header(HttpHeaders.RANGE, range);
        }
        HttpResponse<InputStream> upstream;
        try {
            upstream = UPSTREAM.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("回源失败 url={} cause={}", url, e.getMessage());
            return upstreamError(HttpStatus.BAD_GATEWAY, "上游内容暂不可取");
        }
        // 只透传 2xx（含 Range 的 206）；3xx 因禁用重定向、4xx/5xx 一律按上游不可用返回占位
        if (upstream.statusCode() < 200 || upstream.statusCode() >= 300) {
            upstream.body().close();
            return upstreamError(HttpStatus.BAD_GATEWAY, "上游内容不可用 HTTP " + upstream.statusCode());
        }
        HttpHeaders headers = new HttpHeaders();
        copyHeader(upstream, headers, HttpHeaders.CONTENT_TYPE);
        copyHeader(upstream, headers, HttpHeaders.CONTENT_LENGTH);
        copyHeader(upstream, headers, HttpHeaders.CONTENT_RANGE);
        copyHeader(upstream, headers, HttpHeaders.ACCEPT_RANGES);
        return new ResponseEntity<>(new InputStreamResource(upstream.body()), headers,
                HttpStatus.valueOf(upstream.statusCode()));
    }

    private void copyHeader(HttpResponse<?> upstream, HttpHeaders target, String name) {
        upstream.headers().firstValue(name).ifPresent(value -> target.set(name, value));
    }

    /** 回源失败走非 2xx 纯文本，前端据此显示占位态而不是把错误 JSON 当成媒体 */
    private ResponseEntity<Resource> upstreamError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Operation(summary = "上传查询文件（以图/音/视搜检索）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("upload")
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResult.success(Map.of("path", fileService.saveQueryFile(file)));
    }

    /** Range 单段解析与 206 响应；无 Range 返回 200 全量 */
    private ResponseEntity<Resource> serve(LogicalFile logical, long length, String rangeHeader)
            throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(logical.mime()));
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
