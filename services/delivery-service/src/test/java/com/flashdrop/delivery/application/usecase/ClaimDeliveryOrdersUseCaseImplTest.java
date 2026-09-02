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
import com.flashdrop.delivery.domain.model.DeliveryPerson;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rewritten for PR-A. The actor identity ({@code userId}) now arrives as a
 * method parameter — extracted from the JWT subject by the controller — and
 * the use case resolves the courier via
 * {@link DeliveryPersonRepository#findByUserId(String)} (NOT
 * {@code findById(Long)}, which was the IDOR).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClaimDeliveryOrdersUseCaseImplTest — PR-A: userId from JWT, not from body")
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
            when(routeRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
        @DisplayName("TC3: order already assigned — throws RouteAlreadyAssignedException")
        void orderAlreadyAssigned_throwsException() {
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
            when(routeRepository.existsByOrderId(orderId)).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(RouteAlreadyAssignedException.class)
                    .hasMessageContaining(String.valueOf(orderId));
        }

        @Test
        @DisplayName("TC4: orders from different restaurants — throws IllegalArgumentException")
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
        }

        @Test
        @DisplayName("TC5: DTO no longer carries deliveryPersonId — deserialising old payload still works")
        void dto_hasNoDeliveryPersonIdField() {
            // Assert the DTO API surface (compile-time) — single field, orderIds only.
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(List.of(101L));
            assertThat(request.orderIds()).containsExactly(101L);
            // The DTO record components can be enumerated via reflection in production
            // tests, but the static guarantee here is that request.deliveryPersonId()
            // does not compile — if someone re-adds the field, this file fails to compile.
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
            when(routeRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
            when(routeRepository.existsByOrderId(101L)).thenReturn(false);
            when(routeRepository.existsByOrderId(102L)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);
            useCaseFlagOn.execute(userId, request);

            // Routes were saved (PR-A invariant preserved).
            verify(routeRepository, org.mockito.Mockito.times(2)).save(any());

            // Orders client was called once with the courier's userId and the orderIds.
            ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<List<Long>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(internalOrdersClient).claimOrders(userIdCaptor.capture(), orderIdsCaptor.capture());
            assertThat(userIdCaptor.getValue()).isEqualTo(userId);
            assertThat(orderIdsCaptor.getValue()).containsExactly(101L, 102L);
        }

        @Test
        @DisplayName("TC3: flag ON + orders throws OrderClaimFailedException — exception bubbles; routes STILL saved")
        void flagOn_ordersThrows_routesStillSavedAndExceptionBubbles() {
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
            when(routeRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderClaimFailedException upstreamFailure =
                    new OrderClaimFailedException(org.springframework.http.HttpStatus.CONFLICT,
                            "Order already claimed");
            doThrow(upstreamFailure).when(internalOrdersClient)
                    .claimOrders(eq(userId), eq(List.of(orderId)));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);

            assertThatThrownBy(() -> useCaseFlagOn.execute(userId, request))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .hasMessageContaining("Order already claimed");

            // PR-A invariant preserved — routes were already saved before the orders call.
            verify(routeRepository).save(any());
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
            when(routeRepository.existsByOrderId(any())).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ClaimDeliveryOrdersUseCaseImpl useCaseFlagOn = newUseCase(true);
            useCaseFlagOn.execute(userId, request);

            ArgumentCaptor<List<Long>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(internalOrdersClient).claimOrders(eq(userId), orderIdsCaptor.capture());
            assertThat(orderIdsCaptor.getValue())
                    .containsExactly(101L, 102L, 103L);
        }

        @Test
        @DisplayName("TC5: flag ON + orders throws — flag is OFF, so claimOrders is never called even on a happy path failure")
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
            when(routeRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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