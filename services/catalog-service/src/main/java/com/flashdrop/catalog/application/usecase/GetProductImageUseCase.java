package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductImageStorage;
import com.flashdrop.catalog.application.storage.ProductImageContent;

@Service
public class GetProductImageUseCase {

    private final ProductImageStorage productImageStorage;

    public GetProductImageUseCase(ProductImageStorage productImageStorage) {
        this.productImageStorage = productImageStorage;
    }

    public ProductImageContent execute(String objectKey) {
        if (objectKey == null
                || !objectKey.matches("products/\\d{4}/(0[1-9]|1[0-2])/"
                        + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Object key de imagen invalido");
        }
        return productImageStorage.load(objectKey);
    }
}
