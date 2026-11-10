package com.hsmty.cmr.file;

import java.io.InputStream;

/**
 * 文件存储抽象。
 *
 * <p>local 仅用于本地开发；生产走 S3/MinIO。切换实现不影响 Controller 与业务 Service。
 *
 * @author cmr
 * @since 2026-11-10
 */
public interface FileStore {

    /**
     * 存储类型：local / s3。
     */
    String storeType();

    /**
     * 写入对象。
     *
     * @param inputStream  输入流，由调用方负责关闭
     * @param size         字节数
     * @param contentType  MIME 类型
     * @param originalName 原始文件名（用于提取扩展名）
     * @return 存储位置
     */
    StoredObject put(InputStream inputStream, long size, String contentType, String originalName);

    /**
     * 读取对象。
     */
    InputStream get(String objectKey);

    /**
     * 删除对象。
     */
    void delete(String objectKey);

    /**
     * 存储位置。
     */
    record StoredObject(String bucket, String objectKey) {
    }
}
