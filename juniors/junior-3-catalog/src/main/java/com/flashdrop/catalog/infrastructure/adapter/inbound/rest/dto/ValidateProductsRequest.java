package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

// DTO de entrada para validar productos desde otra logica, como pedidos.
public record ValidateProductsRequest(
        @NotNull
        List<Long> productIds
) {
}
