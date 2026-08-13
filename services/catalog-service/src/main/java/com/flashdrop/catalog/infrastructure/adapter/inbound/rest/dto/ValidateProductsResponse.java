package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

// DTO de salida: indica cuales productos existen y cuales faltan.
public record ValidateProductsResponse(
        boolean valid,
        List<ProductResponse> products,
        List<Long> missingIds
) {
}
