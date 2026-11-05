package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStoreHoursRequest(
        @NotNull @Size(max = 7) List<@Valid @NotNull OpeningHoursDto> hours
) {
}
