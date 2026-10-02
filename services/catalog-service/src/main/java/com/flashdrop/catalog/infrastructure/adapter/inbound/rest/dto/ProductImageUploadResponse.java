package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.catalog.application.storage.StoredProductImage;

public record ProductImageUploadResponse(String objectKey, String url) {

    public static ProductImageUploadResponse from(StoredProductImage image) {
        return new ProductImageUploadResponse(image.objectKey(), image.url());
    }
}
