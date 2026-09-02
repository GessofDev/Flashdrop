package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.CreateDeliveryRouteRequest;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import com.flashdrop.observability.config.ObservabilityAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for InternalRoutesController.
 * Validates JSON request/response shape and ApiResponse envelope contract
 * expected by other services.
 */
@WebMvcTest(InternalRoutesController.class)
@Import(ObservabilityAutoConfiguration.class)
@TestPropertySource(properties = {
        "internal.api.key=test-internal-key",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@DisplayName("InternalRoutesControllerContractTest — KAN-47: JSON envelope contract")
class InternalRoutesControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RouteRepository routeRepository;

    /** JwtAuthenticationFilter is a Filter bean picked up by @WebMvcTest; it
     *  requires JwksKeyProvider via constructor injection. */
    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    @Test
    @DisplayName("TC1: POST /api/internal/routes returns ApiResponse envelope with success=true and 201")
    void createRouteReturnsSuccessEnvelope() throws Exception {
        DeliveryRoute saved = new DeliveryRoute(
                1L, 100L, "Pickup A", "Delivery B",
                Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                RouteStatus.ASSIGNED, Instant.now()
        );
        when(routeRepository.save(any(DeliveryRoute.class))).thenReturn(saved);

        mockMvc.perform(post("/api/internal/routes")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 100,
                                  "pickupAddress": "Pickup A",
                                  "deliveryAddress": "Delivery B",
                                  "distanceKm": 5.0,
                                  "estimatedMinutes": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.pickupAddress").value("Pickup A"));
    }

    @Test
    @DisplayName("TC2: POST /api/internal/routes with invalid body returns 400")
    void createRouteWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/internal/routes")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": null,
                                  "pickupAddress": "",
                                  "deliveryAddress": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC3: PATCH /api/internal/routes/1/status returns ApiResponse envelope with success=true")
    void updateStatusReturnsSuccessEnvelope() throws Exception {
        DeliveryRoute updated = new DeliveryRoute(
                1L, 100L, "Pickup A", "Delivery B",
                Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                RouteStatus.ENTREGADO, Instant.now()
        );
        when(routeRepository.updateStatus(eq(1L), eq("Entregado"))).thenReturn(updated);

        mockMvc.perform(patch("/api/internal/routes/1/status")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "Entregado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ENTREGADO"));
    }

    @Test
    @DisplayName("TC4: POST /api/internal/routes without API key returns 401")
    void createRouteWithoutKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/internal/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 100,
                                  "pickupAddress": "Pickup A",
                                  "deliveryAddress": "Delivery B"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC5: PATCH /api/internal/routes/order/{orderId}/status returns ApiResponse envelope with success=true")
    void updateStatusByOrderIdReturnsSuccessEnvelope() throws Exception {
        DeliveryRoute existing = new DeliveryRoute(
                42L, 100L, "Pickup A", "Delivery B",
                Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                RouteStatus.ASSIGNED, Instant.now()
        );
        DeliveryRoute updated = new DeliveryRoute(
                42L, 100L, "Pickup A", "Delivery B",
                Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                RouteStatus.ENTREGADO, Instant.now()
        );
        when(routeRepository.findByOrderId(eq(100L))).thenReturn(java.util.Optional.of(existing));
        when(routeRepository.updateStatus(eq(42L), eq("Entregado"))).thenReturn(updated);

        mockMvc.perform(patch("/api/internal/routes/order/100/status")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "Entregado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.status").value("ENTREGADO"));
    }
}
