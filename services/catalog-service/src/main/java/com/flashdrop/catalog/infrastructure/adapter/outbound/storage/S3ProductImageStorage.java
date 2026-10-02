package com.flashdrop.catalog.infrastructure.adapter.outbound.storage;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.flashdrop.catalog.application.port.outbound.ProductImageStorage;
import com.flashdrop.catalog.application.storage.ProductImageContent;
import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.domain.exception.ImageStorageException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.infrastructure.config.S3StorageProperties;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3ProductImageStorage implements ProductImageStorage {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public S3ProductImageStorage(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StoredProductImage store(byte[] content, String contentType, String extension) {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        String objectKey = "products/%d/%02d/%s.%s".formatted(
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                extension
        );

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(content)
            );
            return new StoredProductImage(objectKey, publicUrl(objectKey));
        } catch (SdkException exception) {
            throw new ImageStorageException("No se pudo guardar la imagen del producto", exception);
        }
    }

    @Override
    public ProductImageContent load(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build()
            );
            String contentType = response.response().contentType();
            return new ProductImageContent(
                    response.asByteArray(),
                    contentType == null ? "application/octet-stream" : contentType
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ResourceNotFoundException("Product image not found: " + objectKey);
            }
            throw new ImageStorageException("No se pudo obtener la imagen del producto", exception);
        } catch (SdkException exception) {
            throw new ImageStorageException("No se pudo obtener la imagen del producto", exception);
        }
    }

    private String publicUrl(String objectKey) {
        return properties.publicUrlBase().replaceAll("/+$", "") + "/" + objectKey;
    }
}
