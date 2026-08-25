package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.observability.config.ObservabilityAutoConfiguration;
import com.flashdrop.observability.security.InternalApiKeyFilter;
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

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalDeliveryPersonsController.class)
@Import(ObservabilityAutoConfiguration.class)
@TestPropertySource(properties = {
        "internal.api.key=test-internal-key"
})
class InternalDeliveryPersonsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    private DeliveryPersonResponse makePersonResponse(Long id, String userId) {
        return new DeliveryPersonResponse(id, userId, "MOTO", Instant.parse("2024-01-01T10:00:00Z"));
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario 4: GET /api/internal/delivery-persons?userId=... — missing X-Internal-Api-Key → 401
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 4: Missing X-Internal-Api-Key")
    class MissingApiKey {

        @Test
        @DisplayName("returns 401 with ApiError body")
        void missingKey_returns401() throws Exception {
            mockMvc.perform(get("/api/internal/delivery-persons")
                            .param("userId", "U1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.service").value("shared-observability"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario 5: GET /api/internal/delivery-persons?userId=... — wrong X-Internal-Api-Key → 401
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 5: Wrong X-Internal-Api-Key")
    class WrongApiKey {

        @Test
        @DisplayName("returns 401 with ApiError body")
        void wrongKey_returns401() throws Exception {
            mockMvc.perform(get("/api/internal/delivery-persons")
                            .param("userId", "U1")
                            .header(INTERNAL_KEY_HEADER, "wrong-key")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.service").value("shared-observability"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario 6: GET /api/internal/delivery-persons?userId=... — correct key + valid userId → 200
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 6: Correct key + existing delivery person")
    class CorrectKeyExistingPerson {

        @Test
        @DisplayName("returns 200 with ApiResponse containing DeliveryPerson")
        void correctKey_existingUser_returns200() throws Exception {
            when(deliveryPersonRepository.findByUserId("U1"))
                    .thenReturn(Optional.of(new DeliveryPerson(1L, "U1", VehicleType.MOTO, Instant.parse("2024-01-01T10:00:00Z"))));

            mockMvc.perform(get("/api/internal/delivery-persons")
                            .param("userId", "U1")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value("U1"));
        }

        @Test
        @DisplayName("returns 404 when delivery person not found")
        void correctKey_nonExistentUser_returns404() throws Exception {
            when(deliveryPersonRepository.findByUserId("UNKNOWN")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/internal/delivery-persons")
                            .param("userId", "UNKNOWN")
                            .header(INTERNAL_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }
}
