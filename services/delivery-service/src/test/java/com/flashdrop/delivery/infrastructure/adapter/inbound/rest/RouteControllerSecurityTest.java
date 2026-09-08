package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteControllerSecurityTest {

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

    @Nested
    @DisplayName("GET /delivery/routes — security filters enabled")
    @AutoConfigureMockMvc(addFilters = true)
    class WithSecurityFilters {

        /**
         * Injects a pre-established Authentication into SecurityContextHolder BEFORE
         * the MockMvc request is executed. This is read by the controller's
         * extractUserIdFromSecurityContext() without needing a real JWT.
         * addFilters=true ensures the request passes through the security filter chain.
         */
        private void injectAuth(String userId) {
            Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }

        /**
         * SCN-1: Authenticated courier with courier profile → 200 with filtered routes.
         */
        @Test
        @DisplayName("SCN-1: JWT present + courier profile exists — 200 with filtered routes")
        void authenticatedWithCourierProfile_returns200() throws Exception {
            injectAuth("42");
            when(deliveryPersonRepository.findByUserId("42")).thenReturn(Optional.of(new DeliveryPerson(7L, "42", null, null)));
            when(listDeliveryRoutesUseCase.execute(eq(7L))).thenReturn(List.of(
                    makeRouteResponse(1L, "Pendiente"),
                    makeRouteResponse(2L, "Pendiente")
            ));

            mockMvc.perform(get("/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        /**
         * SCN-1b: /api/delivery/routes alias — same behavior.
         */
        @Test
        @DisplayName("SCN-1b: GET /api/delivery/routes (aliased path) — 200 with filtered routes")
        void apiAlias_authenticatedWithCourierProfile_returns200() throws Exception {
            injectAuth("42");
            when(deliveryPersonRepository.findByUserId("42")).thenReturn(Optional.of(new DeliveryPerson(7L, "42", null, null)));
            when(listDeliveryRoutesUseCase.execute(eq(7L))).thenReturn(List.of(makeRouteResponse(1L, "Pendiente")));

            mockMvc.perform(get("/api/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }

        /**
         * SCN-4: No JWT / no authenticated principal → 401.
         * With addFilters=true and no injectAuth(), SecurityContextHolder has no auth,
         * so extractUserIdFromSecurityContext() returns null → controller returns 401.
         */
        @Test
        @DisplayName("SCN-4: missing JWT — 401")
        void noJwt_returns401() throws Exception {
            mockMvc.perform(get("/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * SCN-3: Authenticated user but no courier profile → 401.
         */
        @Test
        @DisplayName("SCN-3: JWT present but no courier profile — 401")
        void jwtButNoCourierProfile_returns401() throws Exception {
            injectAuth("42");
            when(deliveryPersonRepository.findByUserId("42")).thenReturn(Optional.empty());

            mockMvc.perform(get("/delivery/routes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
