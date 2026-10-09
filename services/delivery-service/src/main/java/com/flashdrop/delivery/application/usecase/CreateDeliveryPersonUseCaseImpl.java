package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.CreateDeliveryPersonUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Courier self-signup use case (WU-4 of the openspec-feedback fix).
 *
 * <p>Identity is the {@code userId} passed by the controller — which the
 * controller resolved from the JWT subject via
 * {@code CurrentUserResolver}, never from the request body. This is the
 * same IDOR-closed pattern as {@code ClaimDeliveryOrdersUseCaseImpl}.
 *
 * <p>Idempotency is intentionally NOT supported: if a profile already
 * exists, the use case throws
 * {@link DeliveryPersonAlreadyExistsException} (mapped to 409). This is
 * the simplest contract — the client knows the signup is a no-op without
 * having to compare versions or deal with 200-vs-201 ambiguity.
 */
@Service
public class CreateDeliveryPersonUseCaseImpl implements CreateDeliveryPersonUseCase {

    private final DeliveryPersonRepository deliveryPersonRepository;

    public CreateDeliveryPersonUseCaseImpl(DeliveryPersonRepository deliveryPersonRepository) {
        this.deliveryPersonRepository = deliveryPersonRepository;
    }

    @Override
    public DeliveryPersonResponse execute(Long userId, CreateDeliveryPersonRequest request) {
        String userIdStr = Long.toString(userId);
        if (deliveryPersonRepository.existsByUserId(userIdStr)) {
            throw new DeliveryPersonAlreadyExistsException(userIdStr);
        }
        DeliveryPerson input = new DeliveryPerson(
                null,
                userIdStr,
                request != null ? request.vehicle() : null,
                Instant.now());
        DeliveryPerson saved = deliveryPersonRepository.save(input);
        return toResponse(saved);
    }

    private DeliveryPersonResponse toResponse(DeliveryPerson person) {
        return new DeliveryPersonResponse(
                person.getId(),
                person.getUserId(),
                person.getVehicle() != null ? person.getVehicle().name() : null,
                person.getCreatedAt());
    }
}
