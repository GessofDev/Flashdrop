package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalRoutesController.class)
@Import(com.flashdrop.observability.config.ObservabilityAutoConfiguration.class)
@TestPropertySource(properties = {
        "internal.api.key=test-internal-key"
})
class InternalRoutesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RouteRepository routeRepository;

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    // ---------------------------------------------------------------------------------------------
    // Scenario 7: POST /api/internal/routes — valid body → 201
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 7: POST /api/internal/routes — valid body")
    class CreateRouteValid {

        @Test
        @DisplayName("returns 201 with ApiResponse containing DeliveryRoute")
        void createRoute_valid_returns201() throws Exception {
            DeliveryRoute saved = new DeliveryRoute(
                    1L, 1001L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(3.5)), EstimatedTime.of(20),
                    RouteStatus.ASSIGNED, Instant.parse("2024-01-01T10:00:00Z")
            );
            when(routeRepository.save(any(DeliveryRoute.class))).thenReturn(saved);

            String body = """
                {
                  "orderId": 1001,
                  "pickupAddress": "Pickup A",
                  "deliveryAddress": "Delivery B",
                  "distanceKm": 3.5,
                  "estimatedMinutes": 20
                }
                """;

            mockMvc.perform(post("/api/internal/routes")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(1001))
                    .andExpect(jsonPath("$.data.pickupAddress").value("Pickup A"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario 8: POST /api/internal/routes — invalid body → 400
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 8: POST /api/internal/routes — invalid/missing fields")
    class CreateRouteInvalid {

        @Test
        @DisplayName("returns 400 when orderId is missing")
        void createRoute_missingOrderId_returns400() throws Exception {
            String body = """
                {
                  "pickupAddress": "Pickup A",
                  "deliveryAddress": "Delivery B"
                }
                """;

            mockMvc.perform(post("/api/internal/routes")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when pickupAddress is blank")
        void createRoute_blankPickupAddress_returns400() throws Exception {
            String body = """
                {
                  "orderId": 1001,
                  "pickupAddress": "",
                  "deliveryAddress": "Delivery B"
                }
                """;

            mockMvc.perform(post("/api/internal/routes")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario 9: PATCH /api/internal/routes/{orderId}/status — valid status → 200
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 9: PATCH /api/internal/routes/{orderId}/status")
    class UpdateRouteStatus {

        @Test
        @DisplayName("returns 200 with updated DeliveryRoute")
        void updateStatus_valid_returns200() throws Exception {
            DeliveryRoute updated = new DeliveryRoute(
                    1L, 1001L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(3.5)), EstimatedTime.of(20),
                    RouteStatus.ENTREGADO, Instant.parse("2024-01-01T10:00:00Z")
            );
            when(routeRepository.updateStatus(1L, "ENTREGADO")).thenReturn(updated);

            mockMvc.perform(patch("/api/internal/routes/1/status")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ENTREGADO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ENTREGADO"));
        }
    }
}
