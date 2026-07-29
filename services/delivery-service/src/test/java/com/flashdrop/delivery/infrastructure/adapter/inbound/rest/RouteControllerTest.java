package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;

    @MockBean
    private UpdateRouteStatusUseCase updateRouteStatusUseCase;

    private RouteResponse makeRouteResponse(Long id, String status) {
        return new RouteResponse(id, 101L,
                "Pickup St", "Delivery St",
                3.5, 20, status, Instant.now());
    }

    @Nested
    @DisplayName("GET /delivery/routes")
    class ListRoutes {

        @Test
        @DisplayName("TC1: GET /delivery/routes without deliveryPersonId — returns 200 with ApiResponse wrapper")
        void listRoutes_withoutDeliveryPersonId_returns200WithApiResponse() throws Exception {
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of(makeRouteResponse(1L, "PENDIENTE")));

            mockMvc.perform(get("/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }

        @Test
        @DisplayName("TC2: GET /api/delivery/routes (aliased path) — returns same ApiResponse shape")
        void listRoutes_apiAlias_returnsSameShape() throws Exception {
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of(makeRouteResponse(1L, "PENDIENTE")));

            mockMvc.perform(get("/api/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("TC3: GET /delivery/routes with deliveryPersonId — returns 200 (param optional)")
        void listRoutes_withDeliveryPersonId_returns200() throws Exception {
            when(listDeliveryRoutesUseCase.execute(42L))
                    .thenReturn(List.of(makeRouteResponse(1L, "PENDIENTE")));

            mockMvc.perform(get("/delivery/routes")
                            .param("deliveryPersonId", "42")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }
    }

    @Nested
    @DisplayName("PUT /delivery/routes/{routeId}/status")
    class UpdateStatus {

        @Test
        @DisplayName("TC1: PUT /delivery/routes/{id}/status with valid status — returns 200 wrapped in ApiResponse")
        void updateStatus_valid_returns200WithApiResponse() throws Exception {
            Long routeId = 1L;
            RouteResponse updated = makeRouteResponse(routeId, "ENTREGADO");
            when(updateRouteStatusUseCase.execute(eq(routeId), any(UpdateRouteStatusRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/delivery/routes/{routeId}/status", routeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ENTREGADO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ENTREGADO"));
        }
    }
}
