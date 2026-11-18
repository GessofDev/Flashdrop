package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.usecase.GetDeliveryPersonRatingStatsUseCase;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/delivery-persons")
public class InternalDeliveryPersonsController {

    private static final Logger log = LoggerFactory.getLogger(InternalDeliveryPersonsController.class);

    private final DeliveryPersonRepository deliveryPersonRepository;
    private final GetDeliveryPersonRatingStatsUseCase getDeliveryPersonRatingStatsUseCase;

    public InternalDeliveryPersonsController(
            DeliveryPersonRepository deliveryPersonRepository,
            GetDeliveryPersonRatingStatsUseCase getDeliveryPersonRatingStatsUseCase
    ) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.getDeliveryPersonRatingStatsUseCase = getDeliveryPersonRatingStatsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DeliveryPersonResponse>> getByUserId(
            @RequestParam String userId) {
        log.info("GET /api/internal/delivery-persons?userId={}", userId);

        return deliveryPersonRepository.findByUserId(userId)
                .map(this::toResponse)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private DeliveryPersonResponse toResponse(DeliveryPerson person) {
        // El promedio sale de la cache local (pull a reviews-service), no de una
        // llamada en linea: el perfil se sirve aunque reviews-service caiga.
        RatingSummary rating = getDeliveryPersonRatingStatsUseCase
                .executeForUserId(person.getUserId());
        return new DeliveryPersonResponse(
                person.getId(),
                person.getUserId(),
                person.getVehicle() != null ? person.getVehicle().name() : null,
                person.getCreatedAt(),
                rating.average(),
                rating.count()
        );
    }
}
