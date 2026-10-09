package com.flashdrop.delivery.application.port.inbound;

import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;

/**
 * Inbound port for the courier self-signup flow (WU-4 of the
 * openspec-feedback fix).
 *
 * <p>Lets a user with role {@code Repartidor} create their own delivery
 * profile in delivery-service. The identity is the JWT subject — the
 * controller passes the userId resolved from {@code SecurityContextHolder},
 * never a value from the request body. Idempotency is not the goal: if the
 * profile already exists, the use case throws
 * {@link com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException}
 * (409) so the caller knows the signup is a no-op.
 */
public interface CreateDeliveryPersonUseCase {

    /**
     * @param userId the courier's userId, resolved from the JWT subject
     * @param request the signup payload (vehicle is optional)
     * @return the created delivery person
     * @throws com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException
     *         if a profile with that userId already exists
     */
    DeliveryPersonResponse execute(Long userId, CreateDeliveryPersonRequest request);
}
