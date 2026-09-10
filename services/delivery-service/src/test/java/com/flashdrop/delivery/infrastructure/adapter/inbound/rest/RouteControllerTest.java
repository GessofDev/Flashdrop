package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;

    @MockBean
    private UpdateRouteStatusUseCase updateRouteStatusUseCase;

    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    private RouteResponse makeRouteResponse(Long id, String status) {
        return new RouteResponse(id, 101L,
                "Pickup St", "Delivery St",
                3.5, 20, status, Instant.now(), "ORD-001");
    }

    @Nested
    @DisplayName("PUT /delivery/routes/{routeId}/status")
    class UpdateStatus {

        @Test
        @DisplayName("TC1: PUT /delivery/routes/{id}/status with valid status — returns 200 wrapped in ApiResponse")
        void updateStatus_valid_returns200WithApiResponse() throws Exception {
            Long routeId = 1L;
            RouteResponse updated = makeRouteResponse(routeId, "Entregado");
            when(updateRouteStatusUseCase.execute(eq(routeId), any(UpdateRouteStatusRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/delivery/routes/{routeId}/status", routeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ENTREGADO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("Entregado"));
        }
    }
}
