package com.hsmty.cmr.file;

import com.hsmty.cmr.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * 文件存储装配：local 或 s3。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStoreConfiguration {

    private final FileStorageProperties properties;

    @Bean
    public FileStore fileStore() {
        if ("s3".equalsIgnoreCase(properties.getStore())) {
            return new S3FileStore(buildS3Client(), properties.getBucket());
        }
        return new LocalFileStore(properties.getLocalRoot());
    }

    private S3Client buildS3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        builder.forcePathStyle(properties.isPathStyle());
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }
}
