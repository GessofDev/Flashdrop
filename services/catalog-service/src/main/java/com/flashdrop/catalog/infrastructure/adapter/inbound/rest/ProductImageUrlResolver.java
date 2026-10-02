package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductImageUrlResolver {

    private final String publicUrlBase;

    public ProductImageUrlResolver(@Value("${catalog.storage.s3.public-url-base}") String publicUrlBase) {
        this.publicUrlBase = publicUrlBase.replaceAll("/+$", "");
    }

    public String resolve(String image) {
        if (image == null || image.isBlank()) {
            return image;
        }
        if (!image.startsWith("products/")) {
            return image;
        }
        return publicUrlBase + "/" + image;
    }
}
