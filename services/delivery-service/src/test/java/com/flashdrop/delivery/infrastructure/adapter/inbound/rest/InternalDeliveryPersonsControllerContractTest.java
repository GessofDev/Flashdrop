package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
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

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for InternalDeliveryPersonsController.
 * Validates that the JSON response envelope matches the ApiResponse contract
 * expected by other services: { success: true|false, message: string|null, data: T|null }
 */
@WebMvcTest(InternalDeliveryPersonsController.class)
@Import(ObservabilityAutoConfiguration.class)
@TestPropertySource(properties = {"internal.api.key=test-internal-key"})
@DisplayName("InternalDeliveryPersonsControllerContractTest — KAN-47: JSON envelope contract")
class InternalDeliveryPersonsControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    @Test
    @DisplayName("TC1: GET /api/internal/delivery-persons?userId=U1 returns ApiResponse envelope with success=true")
    void getExistingUserReturnsSuccessEnvelope() throws Exception {
        DeliveryPerson person = new DeliveryPerson(1L, "U1", null, Instant.parse("2024-07-29T10:00:00Z"));
        when(deliveryPersonRepository.findByUserId("U1")).thenReturn(Optional.of(person));

        mockMvc.perform(get("/api/internal/delivery-persons")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .param("userId", "U1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.userId").value("U1"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("TC2: GET /api/internal/delivery-persons?userId=UNKNOWN returns 404 with no body (not-found)")
    void getUnknownUserReturns404() throws Exception {
        when(deliveryPersonRepository.findByUserId("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/delivery-persons")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .param("userId", "UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC3: GET /api/internal/delivery-persons without key returns 401")
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/internal/delivery-persons")
                        .param("userId", "U1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC4: ApiResponse envelope has correct structure — success field is boolean")
    void envelopeHasCorrectSuccessType() throws Exception {
        DeliveryPerson person = new DeliveryPerson(2L, "U2", null, Instant.now());
        when(deliveryPersonRepository.findByUserId("U2")).thenReturn(Optional.of(person));

        mockMvc.perform(get("/api/internal/delivery-persons")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY)
                        .param("userId", "U2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.userId").isString());
    }
}
