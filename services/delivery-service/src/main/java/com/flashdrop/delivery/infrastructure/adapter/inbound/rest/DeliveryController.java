package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/delivery", "/api/delivery"})
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    public DeliveryController(ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase) {
        this.claimDeliveryOrdersUseCase = claimDeliveryOrdersUseCase;
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<List<DeliveryPersonResponse>>> claimDelivery(
            @Valid @RequestBody ClaimDeliveryRequest request) {
        log.info("POST /delivery/claim - Claiming delivery orders: {}", request.orderIds());
        List<DeliveryPersonResponse> response = claimDeliveryOrdersUseCase.execute(request);
        return new ResponseEntity<>(
                ApiResponse.success("Delivery claimed successfully", response),
                HttpStatus.CREATED);
    }
}