package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    private DeliveryPersonResponse makePersonResponse(Long id) {
        return new DeliveryPersonResponse(id, "1", "MOTO", Instant.now());
    }

    @Nested
    @DisplayName("POST /delivery/claim")
    class ClaimDelivery {

        @Test
        @DisplayName("TC1: POST /delivery/claim with valid request — returns 201 with ApiResponse wrapper")
        void claimDelivery_valid_returns201WithApiResponse() throws Exception {
            when(claimDeliveryOrdersUseCase.execute(any(ClaimDeliveryRequest.class)))
                    .thenReturn(List.of(makePersonResponse(5L)));

            mockMvc.perform(post("/delivery/claim")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"deliveryPersonId\":5,\"orderIds\":[101,102]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(5));
        }

        @Test
        @DisplayName("TC2: POST /api/delivery/claim (aliased path) — returns same ApiResponse shape")
        void claimDelivery_apiAlias_returnsSameShape() throws Exception {
            when(claimDeliveryOrdersUseCase.execute(any(ClaimDeliveryRequest.class)))
                    .thenReturn(List.of(makePersonResponse(5L)));

            mockMvc.perform(post("/api/delivery/claim")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"deliveryPersonId\":5,\"orderIds\":[101]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(5));
        }
    }
}
