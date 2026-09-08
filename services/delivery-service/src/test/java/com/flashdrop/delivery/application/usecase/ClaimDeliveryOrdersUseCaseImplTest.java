package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.InternalOrdersClientPort;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.exception.RouteNotPrecreatedException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plan §9.5 D8 — the claim flow is now UPDATE-based: it looks up routes
 * pre-created by Orders (C-6: {@code POST /api/internal/routes}) and
 * assigns them to the courier via
 * {@link RouteRepository#assignDeliveryPerson(Long, Long)}. Each assign
 * internally takes a {@code SELECT … FOR UPDATE} lock so concurrent
 * claims cannot both win the same {@code orderId}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClaimDeliveryOrdersUseCaseImplTest — D8: UPDATE-based claim with pre-created routes")
class ClaimDeliveryOrdersUseCaseImplTest {

    @Mock
    private DeliveryPersonRepository deliveryPersonRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private OrderServicePort orderServicePort;

    @Mock
    private InternalOrdersClientPort internalOrdersClient;

    private ClaimDeliveryOrdersUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        // Default to flag OFF — PR-A tests assume no PR-B wiring is active.
        useCase = newUseCase(false);
    }

    private ClaimDeliveryOrdersUseCaseImpl newUseCase(boolean delegateToOrdersEnabled) {
        return new ClaimDeliveryOrdersUseCaseImpl(
                deliveryPersonRepository, routeRepository, orderServicePort,
                internalOrdersClient, delegateToOrdersEnabled);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private DeliveryPerson personForUserId(String userId) {
        return new DeliveryPerson(5L, userId, VehicleType.MOTO, Instant.now());
    }

    private OrderServicePort.OrderInfo orderInfo(long orderId, long restaurantId) {
        return new OrderServicePort.OrderInfo(
                orderId, restaurantId, "Pickup " + orderId, "Delivery " + orderId, "ORD-" + orderId);
    }

    /**
     * Mirrors what {@code JpaRouteRepositoryAdapter.assignDeliveryPerson} returns
     * after a successful UPDATE: id + orderId preserved, deliveryPersonId set,
     * status bumped to ASSIGNED, updatedAt touched.
     */
    private DeliveryRoute assignedRoute(long orderId, long deliveryPersonId) {
        return new DeliveryRoute(
                100L + orderId,
                orderId,
                deliveryPersonId,
                "Pickup " + orderId,
                "Delivery " + orderId,
                Distance.of(new java.math.BigDecimal("3.2")),
                EstimatedTime.of(20),
                RouteStatus.ASSIGNED,
                Instant.now());
    }

    // ---------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("execute(Long userId, ClaimDeliveryRequest)")
    class Execute {

        @Test
        @DisplayName("TC1: valid claim with userId from JWT — resolves via findByUserId(String)")
        void validClaim_resolvesByUserIdNotById() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenReturn(assignedRoute(orderId, 5L));

            List<DeliveryPersonResponse> result = useCase.execute(userId, request);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(5L);

            // The IDOR fix: resolution happens via findByUserId(Long.toString(userId)),
            // NOT via findById. Verify the string form was passed and findById was never called.
            ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
            verify(deliveryPersonRepository).findByUserId(userIdCaptor.capture());
            assertThat(userIdCaptor.getValue()).isEqualTo("42");

            verify(deliveryPersonRepository, never()).findById(any());
        }

        @Test
        @DisplayName("TC1b: assignDeliveryPerson invoked with (orderId, courier.id) — D2 invariant")
        void validClaim_assignDeliveryPersonBoundToCourierId() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42"); // DeliveryPerson.id == 5L
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenReturn(assignedRoute(orderId, 5L));

            useCase.execute(userId, request);

            ArgumentCaptor<Long> orderIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> courierIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(routeRepository).assignDeliveryPerson(orderIdCaptor.capture(), courierIdCaptor.capture());
            assertThat(orderIdCaptor.getValue()).isEqualTo(orderId);
            assertThat(courierIdCaptor.getValue())
                    .as("assignDeliveryPerson MUST receive the courier's id, not the userId (D2)")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("TC2: missing delivery person for userId — throws DeliveryPersonNotFoundException")
        void missingDeliveryPerson_throwsException() {
            long userId = 999L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L));

            when(deliveryPersonRepository.findByUserId("999"))
                    .thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(DeliveryPersonNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("TC3: route has not been pre-created by Orders — throws RouteNotPrecreatedException")
        void routeNotPrecreated_throwsException() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenThrow(new RouteNotPrecreatedException(orderId));

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(RouteNotPrecreatedException.class)
                    .hasMessageContaining(String.valueOf(orderId));
        }

        @Test
        @DisplayName("TC4: route already assigned to another courier — throws RouteAlreadyAssignedException")
        void routeAlreadyAssigned_throwsException() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenThrow(new RouteAlreadyAssignedException(orderId));

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(RouteAlreadyAssignedException.class)
                    .hasMessageContaining(String.valueOf(orderId));
        }

        @Test
        @DisplayName("TC5: orders from different restaurants — throws IllegalArgumentException")
        void multiRestaurant_throwsIllegalArgumentException() {
            long userId = 42L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L, 102L));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info1 = orderInfo(101L, 10L);
            OrderServicePort.OrderInfo info2 = orderInfo(102L, 20L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L)))
                    .thenReturn(List.of(info1, info2));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(101L, 102L)))
                    .thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same restaurant");

            verify(routeRepository, never()).assignDeliveryPerson(anyLong(), anyLong());
        }

        @Test
        @DisplayName("TC6: DTO no longer carries deliveryPersonId — deserialising old payload still works")
        void dto_hasNoDeliveryPersonIdField() {
            // Assert the DTO API surface (compile-time) — single field, orderIds only.
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L));
            assertThat(request.orderIds()).containsExactly(101L);
        }

        @Test
        @DisplayName("TC7: batch with one already-claimed route — D8 atomic, no partial persists")
        void batchAtomic_oneAlreadyClaimed_doesNotPersistOthers() {
            long userId = 42L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L, 102L, 103L));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info101 = orderInfo(101L, 10L);
            OrderServicePort.OrderInfo info102 = orderInfo(102L, 10L);
            OrderServicePort.OrderInfo info103 = orderInfo(103L, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L, 103L)))
                    .thenReturn(List.of(info101, info102, info103));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(101L, 102L, 103L)))
                    .thenReturn(true);
            // 101 → success
            when(routeRepository.assignDeliveryPerson(101L, 5L))
                    .thenReturn(assignedRoute(101L, 5L));
            // 102 → already claimed by someone else (race-lost)
            when(routeRepository.assignDeliveryPerson(102L, 5L))
                    .thenThrow(new RouteAlreadyAssignedException(102L));
            // 103 → would have succeeded but the transaction aborts before we get there

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(RouteAlreadyAssignedException.class)
                    .hasMessageContaining("102");

            // D8 invariant: the whole batch is atomic. 101 may have been attempted
            // but 103 must NEVER have been touched (otherwise it would be a partial
            // claim, which the plan forbids).
            verify(routeRepository, never()).assignDeliveryPerson(eq(103L), anyLong());
        }
    }

    // ---------------------------------------------------------------------------------
    // PR-B tests — feature flag gates calls to InternalOrdersClientPort.
    // Default value of the flag is OFF (D8: production behavior unchanged until flip).
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("PR-B — feature flag delivery.claim.delegate-to-orders.enabled")
    class FeatureFlag {

        @Test
        @DisplayName("TC1: flag OFF — internalOrdersClient never invoked (production safe default)")
        void flagOff_doesNotCallInternalOrdersClient() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenReturn(assignedRoute(orderId, 5L));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOff = newUseCase(false);
            useCaseFlagOff.execute(userId, request);

            verifyNoInteractions(internalOrdersClient);
        }

        @Test
        @DisplayName("TC2: flag ON + orders 200 — claimOrders called once with (userId, uniqueOrderIds)")
        void flagOn_success_callsClientOnceWithCorrectArgs() {
            long userId = 42L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L, 102L));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info101 = orderInfo(101L, 10L);
            OrderServicePort.OrderInfo info102 = orderInfo(102L, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L)))
                    .thenReturn(List.of(info101, info102));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(101L, 102L)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(101L, 5L))
                    .thenReturn(assignedRoute(101L, 5L));
            when(routeRepository.assignDeliveryPerson(102L, 5L))
                    .thenReturn(assignedRoute(102L, 5L));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);
            useCaseFlagOn.execute(userId, request);

            // Routes were assigned (D8 invariant preserved — UPDATE-based).
            verify(routeRepository, org.mockito.Mockito.times(2)).assignDeliveryPerson(anyLong(), anyLong());

            // Orders client was called once with the courier's userId and the orderIds.
            ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<List<Long>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(internalOrdersClient).claimOrders(userIdCaptor.capture(), orderIdsCaptor.capture());
            assertThat(userIdCaptor.getValue()).isEqualTo(userId);
            assertThat(orderIdsCaptor.getValue()).containsExactly(101L, 102L);
        }

        @Test
        @DisplayName("TC3: flag ON + orders throws OrderClaimFailedException — exception bubbles; assignments kept")
        void flagOn_ordersThrows_assignmentsKeptAndExceptionBubbles() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenReturn(assignedRoute(orderId, 5L));

            OrderClaimFailedException upstreamFailure =
                    new OrderClaimFailedException(org.springframework.http.HttpStatus.CONFLICT,
                            "Order already claimed");
            doThrow(upstreamFailure).when(internalOrdersClient)
                    .claimOrders(eq(userId), eq(List.of(orderId)));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);

            assertThatThrownBy(() -> useCaseFlagOn.execute(userId, request))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .hasMessageContaining("Order already claimed");

            // Routes were assigned before the orders call — D8 invariant preserved.
            verify(routeRepository).assignDeliveryPerson(orderId, 5L);
        }

        @Test
        @DisplayName("TC4: flag ON + body shape — ArgumentCaptor captures the exact orderIds list")
        void flagOn_bodyShapeAssertion_capturesExactOrderIds() {
            long userId = 7L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L, 102L, 103L));

            DeliveryPerson person = personForUserId("7");
            OrderServicePort.OrderInfo info101 = orderInfo(101L, 10L);
            OrderServicePort.OrderInfo info102 = orderInfo(102L, 10L);
            OrderServicePort.OrderInfo info103 = orderInfo(103L, 10L);

            when(deliveryPersonRepository.findByUserId("7"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L, 103L)))
                    .thenReturn(List.of(info101, info102, info103));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(101L, 102L, 103L)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(anyLong(), anyLong()))
                    .thenAnswer(inv -> assignedRoute(inv.getArgument(0), inv.getArgument(1)));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);
            useCaseFlagOn.execute(userId, request);

            ArgumentCaptor<List<Long>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(internalOrdersClient).claimOrders(eq(userId), orderIdsCaptor.capture());
            assertThat(orderIdsCaptor.getValue())
                    .containsExactly(101L, 102L, 103L);
        }

        @Test
        @DisplayName("TC5: flag OFF + orders throws — flag is OFF, so claimOrders is never called even on a happy path failure")
        void flagOff_onFailure_isNotReached_becauseClientIsNotCalled() {
            long userId = 42L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(orderId));

            DeliveryPerson person = personForUserId("42");
            OrderServicePort.OrderInfo info = orderInfo(orderId, 10L);

            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(info));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.assignDeliveryPerson(orderId, 5L))
                    .thenReturn(assignedRoute(orderId, 5L));

            // Even if the orders client is configured to throw, with the flag OFF it is
            // never reached — this is the production safety property.
            doThrow(new RuntimeException("would-have-thrown"))
                    .when(internalOrdersClient).claimOrders(any(), any());

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOff = newUseCase(false);
            useCaseFlagOff.execute(userId, request);

            verifyNoInteractions(internalOrdersClient);
        }
    }
}
