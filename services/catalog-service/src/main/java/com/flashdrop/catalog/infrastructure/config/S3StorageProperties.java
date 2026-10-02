package com.flashdrop.catalog.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.storage.s3")
public record S3StorageProperties(
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        String publicUrlBase
) {
}
