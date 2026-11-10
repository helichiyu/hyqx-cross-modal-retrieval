package com.hsmty.cmr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。
 *
 * <p>默认 {@code local} 仅用于本地开发；生产必须切换 {@code s3} 并注入 OSS/S3 地址、
 * 桶与凭据。空地址不阻塞启动，实际调用返回稳定业务错误。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@ConfigurationProperties(prefix = "cmr.file")
public class FileStorageProperties {

    /** 存储类型：local / s3 */
    private String store = "local";

    /** 本地存储根目录 */
    private String localRoot = "./data/files";

    /** S3/MinIO 桶 */
    private String bucket;

    /** S3/MinIO 地址 */
    private String endpoint;

    /** 区域 */
    private String region = "us-east-1";

    /** 访问密钥；留空时走默认凭据链 */
    private String accessKey;

    /** 访问密钥 */
    private String secretKey;

    /** 是否使用 path-style（MinIO 通常为 true） */
    private boolean pathStyle = true;
}
