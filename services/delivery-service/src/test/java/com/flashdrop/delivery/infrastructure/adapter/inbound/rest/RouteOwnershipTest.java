package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.application.usecase.UpdateRouteStatusUseCaseImpl;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.exception.GlobalExceptionHandler;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RouteOwnershipTest {
    @Mock private RouteRepository routes;
    @Mock private DeliveryPersonRepository people;
    @Mock private OrderServicePort orders;
    @Mock private ListDeliveryRoutesUseCase listRoutes;
    private MockMvc mvc;
    private AtomicReference<DeliveryRoute> stored;

    @BeforeEach
    void setUp() {
        stored = new AtomicReference<>(route(3L, RouteStatus.ASSIGNED));
        var useCase = new UpdateRouteStatusUseCaseImpl(routes, orders, people);
        mvc = MockMvcBuilders.standaloneSetup(new RouteController(listRoutes, useCase, people,
                        new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private DeliveryRoute route(Long owner, RouteStatus status) {
        return new DeliveryRoute(15L, 25L, owner, "Pickup", "Delivery", Distance.of(BigDecimal.ONE),
                EstimatedTime.of(10), status, Instant.now());
    }

    private void authenticate(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_Repartidor"))));
    }

    private void assertUnchanged() {
        assertThat(stored.get().getStatus()).isEqualTo(RouteStatus.ASSIGNED);
        verify(routes, never()).updateStatus(anyLong(), anyString());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/delivery", "/api/delivery"})
    void update_foreignCourierIncludingUserIdCollision_returns403WithoutWriting(String prefix) throws Exception {
        authenticate("3"); // Same as route owner PROFILE id, but a different Auth user.
        when(people.findByUserId("3")).thenReturn(Optional.of(new DeliveryPerson(2L, "3", null, null)));
        when(routes.findById(15L)).thenReturn(Optional.of(stored.get()));

        mvc.perform(put(prefix + "/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\",\"userId\":4,\"deliveryPersonId\":3}"))
                .andExpect(status().isForbidden());

        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/delivery", "/api/delivery"})
    void update_ownerWithDistinctProfileId_returns200AndWritesCanonicalStatus(String prefix) throws Exception {
        authenticate("4");
        when(people.findByUserId("4")).thenReturn(Optional.of(new DeliveryPerson(3L, "4", null, null)));
        when(routes.findById(15L)).thenReturn(Optional.of(stored.get()));
        when(routes.updateStatus(15L, "En camino")).thenAnswer(invocation -> {
            stored.set(route(3L, RouteStatus.EN_CAMINO));
            return stored.get();
        });
        when(orders.getOrdersByIds(List.of(25L))).thenReturn(List.of(
                new OrderServicePort.OrderInfo(25L, 1L, "Pickup", "Delivery", "FD-25")));

        mvc.perform(put(prefix + "/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"en_camino\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("En camino"))
                .andExpect(jsonPath("$.data.code").value("FD-25"));
        assertThat(stored.get().getStatus()).isEqualTo(RouteStatus.EN_CAMINO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/delivery", "/api/delivery"})
    void update_noAuthentication_returns401BeforeLookup(String prefix) throws Exception {
        mvc.perform(put(prefix + "/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(people, routes, orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/delivery", "/api/delivery"})
    void update_missingProfile_returns403WithoutWriting(String prefix) throws Exception {
        authenticate("4");
        when(people.findByUserId("4")).thenReturn(Optional.empty());
        mvc.perform(put(prefix + "/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\"}"))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    @Test
    void update_unassignedRoute_returns403WithoutWriting() throws Exception {
        authenticate("4");
        when(people.findByUserId("4")).thenReturn(Optional.of(new DeliveryPerson(3L, "4", null, null)));
        when(routes.findById(15L)).thenReturn(Optional.of(route(null, RouteStatus.PENDIENTE)));
        mvc.perform(put("/api/delivery/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\"}"))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    @Test
    void update_nullProfileIdAndUnassignedRoute_returns403WithoutWriting() throws Exception {
        authenticate("4");
        when(people.findByUserId("4")).thenReturn(Optional.of(new DeliveryPerson(null, "4", null, null)));
        when(routes.findById(15L)).thenReturn(Optional.of(route(null, RouteStatus.PENDIENTE)));
        mvc.perform(put("/api/delivery/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\"}"))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    @Test
    void update_missingRoute_returns404WithoutWriting() throws Exception {
        authenticate("4");
        when(people.findByUserId("4")).thenReturn(Optional.of(new DeliveryPerson(3L, "4", null, null)));
        when(routes.findById(15L)).thenReturn(Optional.empty());
        mvc.perform(put("/api/delivery/routes/15/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CAMINO\"}"))
                .andExpect(status().isNotFound());
        assertUnchanged();
    }
}
