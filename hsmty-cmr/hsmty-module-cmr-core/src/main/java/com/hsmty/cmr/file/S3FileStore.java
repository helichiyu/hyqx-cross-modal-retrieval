package com.hsmty.cmr.file;

import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BusinessException;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

/**
 * S3/MinIO 存储。
 *
 * <p>桶或地址未配置时不阻塞启动，实际调用返回稳定的“存储不可用”业务错误。
 *
 * @author cmr
 * @since 2026-11-10
 */
public class S3FileStore implements FileStore {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStore(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String storeType() {
        return "s3";
    }

    @Override
    public StoredObject put(InputStream inputStream, long size, String contentType, String originalName) {
        requireAvailable();
        String objectKey = FileKeys.build(originalName);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(inputStream, size));
        } catch (S3Exception e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "对象存储写入失败", e);
        }
        return new StoredObject(bucket, objectKey);
    }

    @Override
    public InputStream get(String objectKey) {
        requireAvailable();
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            throw new BusinessException(CommonResponseEnum.FILE_NOT_EXISTS, new Object[0], "对象存储读取失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        requireAvailable();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "对象存储删除失败", e);
        }
    }

    private void requireAvailable() {
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0],
                    "对象存储桶未配置（cmr.file.bucket）");
        }
    }
}
