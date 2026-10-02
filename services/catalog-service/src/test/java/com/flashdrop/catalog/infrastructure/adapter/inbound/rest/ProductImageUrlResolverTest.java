package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProductImageUrlResolverTest {

    private final ProductImageUrlResolver resolver = new ProductImageUrlResolver(
            "/catalog/images/"
    );

    @Test
    void resolve_s3ObjectKey_returnsGatewayUrl() {
        assertEquals(
                "/catalog/images/products/2026/09/image.webp",
                resolver.resolve("products/2026/09/image.webp")
        );
    }

    @Test
    void resolve_legacyAsset_keepsOriginalReference() {
        assertEquals("assets/img/burger1.png", resolver.resolve("assets/img/burger1.png"));
    }

    @Test
    void resolve_existingAbsoluteUrl_keepsOriginalReference() {
        assertEquals("https://cdn.example/image.png", resolver.resolve("https://cdn.example/image.png"));
    }
}
