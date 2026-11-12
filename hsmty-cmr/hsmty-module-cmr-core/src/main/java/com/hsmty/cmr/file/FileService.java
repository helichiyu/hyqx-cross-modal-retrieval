package com.hsmty.cmr.file;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.config.CmrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 共享卷文件服务：dataset/runtime 白名单、防穿越、查询文件上传、数据集压缩包安全解压
 *
 * <p>逻辑路径约定（F1 改造后全链统一）：{@code dataset/...}、{@code runtime/...}，与算子侧 project_root 相对路径一致。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"), Map.entry("ogg", "audio/ogg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"),
            Map.entry("avi", "video/x-msvideo"), Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("txt", "text/plain;charset=utf-8"),
            Map.entry("json", "application/json"), Map.entry("jsonl", "application/json"),
            Map.entry("pdf", "application/pdf"), Map.entry("zip", "application/zip"));

    private static final String DATASET = "dataset";
    private static final String RUNTIME = "runtime";

    private final CmrProperties properties;

    /** dataset 卷绝对根 */
    public Path datasetRoot() {
        return Path.of(properties.getVolume().getDataset()).toAbsolutePath().normalize();
    }

    /** runtime 卷绝对根 */
    public Path runtimeRoot() {
        return Path.of(properties.getVolume().getRuntime()).toAbsolutePath().normalize();
    }

    /** 逻辑路径 → 绝对路径（白名单 + 防穿越） */
    public LogicalFile resolve(String logicalPath) {
        BizException.assertNull(logicalPath, "文件路径不能为空");
        String p = logicalPath.replace('\\', '/').trim();
        BizException.assertTrue(p.startsWith("/"), "禁止使用绝对路径");
        BizException.assertTrue(p.contains(".."), "非法路径");
        BizException.assertTrue(p.contains(":"), "非法路径");

        String[] parts = p.split("/", 2);
        BizException.assertTrue(parts.length < 2 || parts[1].isBlank(), "路径必须以 dataset/ 或 runtime/ 开头");

        Path root = switch (parts[0]) {
            case DATASET -> datasetRoot();
            case RUNTIME -> runtimeRoot();
            default -> throw new BizException("路径必须以 dataset/ 或 runtime/ 开头");
        };
        Path absolute = root.resolve(parts[1]).normalize();
        BizException.assertTrue(!absolute.startsWith(root), "路径越界");
        BizException.assertTrue(!Files.exists(absolute) || !Files.isRegularFile(absolute), "文件不存在");
        return new LogicalFile(absolute, p, mimeOf(absolute));
    }

    /** 查询文件上传：dataset/query/{yyyyMM}/{uuid}.{ext} */
    public String saveQueryFile(MultipartFile file) {
        String original = file.getOriginalFilename();
        BizException.assertNull(original, "文件名为空");
        String ext = extOf(original);
        BizException.assertNull(ext, "无法识别文件类型");
        try {
            Path dir = datasetRoot().resolve("query/" + java.time.YearMonth.now());
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
            Path target = dir.resolve(name);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("查询文件上传: {} -> {}", original, toLogical(DATASET, target));
            return toLogical(DATASET, target);
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /** 数据集压缩包安全解压：dataset/ds/{bizNo}/，返回逻辑目录路径 */
    public String extractDataset(String bizNo, MultipartFile file) {
        BizException.assertNull(file.getOriginalFilename(), "文件名为空");
        Path targetDir = datasetRoot().resolve("ds").resolve(bizNo);
        try {
            Files.createDirectories(targetDir);
            Path temp = Files.createTempFile("cmr-ds-", ".zip");
            try {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                unzipSafely(temp, targetDir);
            } finally {
                Files.deleteIfExists(temp);
            }
            log.info("数据集解压完成: {} -> {}", file.getOriginalFilename(), toLogical(DATASET, targetDir));
            return toLogical(DATASET, targetDir);
        } catch (IOException e) {
            throw new BizException("数据集解压失败: " + e.getMessage(), e);
        }
    }

    /** 直接落盘为数据集目录下文件（不经 zip） */
    public String saveDatasetFile(String bizNo, MultipartFile file) {
        String original = file.getOriginalFilename();
        BizException.assertNull(original, "文件名为空");
        try {
            Path dir = datasetRoot().resolve("ds").resolve(bizNo);
            Files.createDirectories(dir);
            Path target = dir.resolve(Path.of(original).getFileName());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return toLogical(DATASET, target);
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /** 写文本文件到 runtime 卷（评测文件等），返回逻辑路径 */
    public String writeRuntimeText(String relative, String content) {
        try {
            Path target = runtimeRoot().resolve(relative).normalize();
            BizException.assertTrue(!target.startsWith(runtimeRoot()), "路径越界");
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return toLogical(RUNTIME, target);
        } catch (IOException e) {
            throw new BizException("文件写入失败: " + e.getMessage(), e);
        }
    }

    /** 写文本文件到 dataset 卷（默认评测文件等；relative 不含 dataset/ 前缀），返回逻辑路径 */
    public String writeDatasetText(String relative, String content) {
        try {
            Path target = datasetRoot().resolve(relative).normalize();
            BizException.assertTrue(!target.startsWith(datasetRoot()), "路径越界");
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return toLogical(DATASET, target);
        } catch (IOException e) {
            throw new BizException("文件写入失败: " + e.getMessage(), e);
        }
    }

    /** 读 runtime/dataset 卷内文本文件 */
    public String readText(String logicalPath) {
        try {
            return Files.readString(resolve(logicalPath).absolute());
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /** 逻辑路径是否存在 */
    public boolean exists(String logicalPath) {
        try {
            resolve(logicalPath);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /** 校验目录逻辑路径（白名单 + 防穿越 + 必须存在且为目录），返回规范化逻辑路径 */
    public String requireDir(String logicalPath) {
        BizException.assertNull(logicalPath, "目录路径不能为空");
        String p = logicalPath.replace('\\', '/').trim().replaceAll("/+$", "");
        BizException.assertTrue(p.startsWith("/"), "禁止使用绝对路径");
        BizException.assertTrue(p.contains("..") || p.contains(":"), "非法路径");
        String[] parts = p.split("/", 2);
        BizException.assertTrue(parts.length < 2 || parts[1].isBlank(), "路径必须以 dataset/ 或 runtime/ 开头");
        Path root = switch (parts[0]) {
            case DATASET -> datasetRoot();
            case RUNTIME -> runtimeRoot();
            default -> throw new BizException("路径必须以 dataset/ 或 runtime/ 开头");
        };
        Path absolute = root.resolve(parts[1]).normalize();
        BizException.assertTrue(!absolute.startsWith(root), "路径越界");
        BizException.assertTrue(!Files.isDirectory(absolute), "目录不存在: " + p);
        return p;
    }

    /** 逐条读取 jsonl（跳过空行与坏行） */
    public java.util.List<String> readLines(String logicalPath) {
        try {
            return Files.readAllLines(resolve(logicalPath).absolute()).stream()
                    .filter(l -> !l.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage(), e);
        }
    }

    private void unzipSafely(Path zip, Path targetDir) throws IOException {
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                BizException.assertTrue(entries > 50000, "压缩包条目过多");
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                // zip slip 防护：解压目标必须落在目标目录内
                BizException.assertTrue(!resolved.startsWith(targetDir), "非法压缩条目: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    try (OutputStream out = Files.newOutputStream(resolved)) {
                        zis.transferTo(out);
                    }
                }
                zis.closeEntry();
            }
        }
        BizException.assertTrue(entries == 0, "压缩包为空");
    }

    private String toLogical(String rootName, Path absolute) {
        String rel = rootName.equals(DATASET)
                ? datasetRoot().relativize(absolute).toString()
                : runtimeRoot().relativize(absolute).toString();
        return rootName + "/" + rel.replace('\\', '/');
    }

    private String mimeOf(Path p) {
        return MIME.getOrDefault(extOf(p.getFileName().toString()), "application/octet-stream");
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
