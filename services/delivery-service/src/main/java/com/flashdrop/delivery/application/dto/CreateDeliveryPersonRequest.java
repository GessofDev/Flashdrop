package com.flashdrop.delivery.application.dto;

import com.flashdrop.delivery.domain.valueobjects.VehicleType;

/**
 * Request body for {@code POST /api/delivery/persons}.
 *
 * <p>The courier's identity ({@code userId}) is derived from the JWT subject
 * at the controller via {@code CurrentUserResolver} — it MUST NOT come from
 * the request body, because that would let any caller create a profile as
 * any other user. The DTO carries only the optional vehicle type.
 *
 * <p>If the field is omitted, the profile is created with {@code vehicle = null}
 * and can be updated later via a follow-up endpoint (out of scope for WU-4).
 *
 * <p>No validation constraints on {@code vehicle}: it's an enum so the
 * only "invalid" values are the ones Jackson can't deserialize, and those
 * surface as a 400 via Spring's HttpMessageNotReadableException handler
 * rather than a Bean Validation failure.
 */
public record CreateDeliveryPersonRequest(
        VehicleType vehicle
) {
}
