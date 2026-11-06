package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.usecase.EraseDeliveryDataUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Eliminacion de cuenta (F5), solo llamadas internas con X-Internal-Api-Key (lo exige el filtro de
 * shared-observability). Ambos endpoints son idempotentes.
 */
@RestController
@RequestMapping("/api/internal/erasure")
public class InternalErasureController {

    private static final Logger log = LoggerFactory.getLogger(InternalErasureController.class);

    private final EraseDeliveryDataUseCase eraseDeliveryData;

    public InternalErasureController(EraseDeliveryDataUseCase eraseDeliveryData) {
        this.eraseDeliveryData = eraseDeliveryData;
    }

    public record AnonymizeRoutesRequest(@NotNull List<Long> orderIds) {
    }

    @PostMapping("/routes/anonymize")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> anonymizeRoutes(
            @Valid @RequestBody AnonymizeRoutesRequest request) {
        int updated = eraseDeliveryData.anonymizeClientRoutes(request.orderIds());
        log.info("ERASURE_ROUTES_ANONYMIZED orders={} updated={}", request.orderIds().size(), updated);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }

    @PostMapping("/delivery-persons")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> eraseCourier(@RequestParam String userId) {
        boolean erased = eraseDeliveryData.eraseCourier(userId);
        log.info("ERASURE_COURIER_PROFILE erased={}", erased);
        return ResponseEntity.ok(ApiResponse.success(Map.of("erased", erased)));
    }
}
