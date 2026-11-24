package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalRoutesControllerGetByOrderTest {

    @Test
    void getByOrderId_existingRoute_returnsRouteWithNumericId() throws Exception {
        RouteRepository routeRepository = mock(RouteRepository.class);
        DeliveryRoute route = new DeliveryRoute(
                77L,
                42L,
                "Av. Providencia 123",
                "Calle Falsa 456",
                Distance.of(new BigDecimal("2.4")),
                EstimatedTime.of(14),
                RouteStatus.EN_CAMINO,
                Instant.parse("2026-11-24T12:00:00Z")
        );
        when(routeRepository.findByOrderId(42L)).thenReturn(Optional.of(route));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InternalRoutesController(routeRepository)).build();

        mockMvc.perform(get("/api/internal/routes/order/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.orderId").value(42))
                .andExpect(jsonPath("$.data.status").value("En camino"));
    }

    @Test
    void getByOrderId_missingRoute_returns404() throws Exception {
        RouteRepository routeRepository = mock(RouteRepository.class);
        when(routeRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InternalRoutesController(routeRepository)).build();

        mockMvc.perform(get("/api/internal/routes/order/99"))
                .andExpect(status().isNotFound());
    }
}
