package com.flashdrop.catalog.infrastructure.adapter.outbound.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.infrastructure.config.S3StorageProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ProductImageStorageTest {

    @Test
    void storeUsesConfiguredBucketAndReturnsStableRelativeUrl() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageProperties properties = new S3StorageProperties(
                "http://localhost:4566",
                "flashdrop-products",
                "us-east-1",
                "test",
                "test",
                "/catalog/images/"
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ProductImageStorage storage = new S3ProductImageStorage(s3Client, properties);

        StoredProductImage result = storage.store(
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                "image/jpeg",
                "jpg"
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("flashdrop-products");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(result.objectKey())
                .matches("products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(result.url()).isEqualTo("/catalog/images/" + result.objectKey());
    }
}
