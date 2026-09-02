package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Updated for PR-A: the controller now extracts the courier's userId from the
 * JWT subject (via {@link SecurityContextHolder}). The request body no
 * longer carries {@code deliveryPersonId} — that field is gone from the DTO
 * and any legacy clients are rejected by validation.
 *
 * <p>{@code addFilters = false} disables Spring Security filters for this
 * slice — the security matcher map is exercised by
 * {@link com.flashdrop.delivery.infrastructure.config.SecurityConfigTest}.
 */
@WebMvcTest(DeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    /**
     * JwtAuthenticationFilter is a Filter bean that @WebMvcTest picks up; it
     * requires JwksKeyProvider via constructor injection. Mocking the provider
     * (a @Component that does NOT trigger constructor side-effects when mocked)
     * lets the filter bean wire up cleanly without us caring about real JWKS.
     */
    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    private DeliveryPersonResponse makePersonResponse(Long id) {
        return new DeliveryPersonResponse(id, "1", "MOTO", Instant.now());
    }

    @BeforeEach
    void setUp() {
        // Provide a fake auth principal so the controller can parse "5" as the userId.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("5", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /delivery/claim")
    class ClaimDelivery {

        @Test
        @DisplayName("TC1: POST /delivery/claim with valid request — returns 201 with ApiResponse wrapper")
        void claimDelivery_valid_returns201WithApiResponse() throws Exception {
            when(claimDeliveryOrdersUseCase.execute(anyLong(), any(ClaimDeliveryRequest.class)))
                    .thenReturn(List.of(makePersonResponse(5L)));

            mockMvc.perform(post("/delivery/claim")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "5", null, List.of())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderIds\":[101,102]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(5));
        }

        @Test
        @DisplayName("TC2: POST /api/delivery/claim (aliased path) — returns same ApiResponse shape")
        void claimDelivery_apiAlias_returnsSameShape() throws Exception {
            when(claimDeliveryOrdersUseCase.execute(anyLong(), any(ClaimDeliveryRequest.class)))
                    .thenReturn(List.of(makePersonResponse(5L)));

            mockMvc.perform(post("/api/delivery/claim")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "5", null, List.of())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderIds\":[101]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(5));
        }

        @Test
        @DisplayName("TC3: POST without auth → 401 (defence-in-depth check in controller)")
        void claimDelivery_noAuth_returns401() throws Exception {
            SecurityContextHolder.clearContext(); // simulate filter not running

            mockMvc.perform(post("/delivery/claim")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderIds\":[101]}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}