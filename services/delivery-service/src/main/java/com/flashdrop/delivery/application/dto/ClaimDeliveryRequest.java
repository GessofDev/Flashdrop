package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/delivery/claim}.
 *
 * <p>Per plan D10 (PR-A, IDOR fix): the courier identity ({@code userId}) is
 * derived from the JWT subject at the controller — it MUST NOT come from the
 * request body, because that would allow any caller to claim orders as any
 * courier. The DTO carries only the orders to claim.
 */
public record ClaimDeliveryRequest(
        @NotEmpty @Size(max = 3) List<Long> orderIds
) {
}