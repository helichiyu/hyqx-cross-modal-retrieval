package com.hsmty.cmr.file;

import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件存储（仅开发/演示）。
 *
 * <p>对象键做路径穿越校验，最终路径必须落在根目录内。
 *
 * @author cmr
 * @since 2026-11-10
 */
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(String rootDir) {
        this.root = Paths.get(rootDir == null ? "./data/files" : rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("本地文件目录创建失败: " + root, e);
        }
    }

    @Override
    public String storeType() {
        return "local";
    }

    @Override
    public StoredObject put(InputStream inputStream, long size, String contentType, String originalName) {
        String objectKey = FileKeys.build(originalName);
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                inputStream.transferTo(outputStream);
            }
        } catch (IOException e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "本地文件写入失败", e);
        }
        return new StoredObject("local", objectKey);
    }

    @Override
    public InputStream get(String objectKey) {
        Path target = resolve(objectKey);
        try {
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new BusinessException(CommonResponseEnum.FILE_NOT_EXISTS, new Object[0], "文件不存在", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "本地文件删除失败", e);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(CommonResponseEnum.VALID_ERROR, new Object[0], "非法对象键");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(CommonResponseEnum.VALID_ERROR, new Object[0], "非法对象键");
        }
        return target;
    }
}
