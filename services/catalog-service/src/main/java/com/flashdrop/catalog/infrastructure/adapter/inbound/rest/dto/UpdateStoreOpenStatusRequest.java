package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStoreOpenStatusRequest(@NotNull Boolean acceptingOrders) {
}
