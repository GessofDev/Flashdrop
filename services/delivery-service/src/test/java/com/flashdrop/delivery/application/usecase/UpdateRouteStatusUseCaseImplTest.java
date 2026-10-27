package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.exception.RouteDeliveryRequiresCodeException;
import com.flashdrop.delivery.domain.exception.RouteNotFoundException;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UpdateRouteStatusUseCaseImplTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private OrderServicePort orderServicePort;

    private UpdateRouteStatusUseCaseImpl useCase;

    @Mock
    private DeliveryPersonRepository deliveryPersonRepository;

    @BeforeEach
    void setUp() {
        useCase = new UpdateRouteStatusUseCaseImpl(routeRepository, orderServicePort, deliveryPersonRepository);
        when(deliveryPersonRepository.findByUserId("42"))
                .thenReturn(Optional.of(new DeliveryPerson(7L, "42", null, null)));
        lenient().when(routeRepository.findById(1L)).thenReturn(Optional.of(new DeliveryRoute(
                1L, 101L, 7L, "Pickup", "Delivery", Distance.of(BigDecimal.ONE),
                EstimatedTime.of(10), RouteStatus.ASSIGNED, Instant.now())));
    }

    @Nested
    @DisplayName("execute(Long routeId, UpdateRouteStatusRequest)")
    class Execute {

        @Test
        @DisplayName("TC1: valid status update — returns updated RouteResponse with code")
        void validStatusUpdate_returnsUpdatedRouteResponse() {
            Long routeId = 1L;
            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("EN_CAMINO");
            DeliveryRoute updated = new DeliveryRoute(routeId, 101L, "Pickup", "Delivery",
                    Distance.of(new BigDecimal("3.5")), EstimatedTime.of(20),
                    RouteStatus.EN_CAMINO, Instant.now());

            when(routeRepository.updateStatus(eq(routeId), any())).thenReturn(updated);
            when(orderServicePort.getOrdersByIds(List.of(101L)))
                    .thenReturn(List.of(new OrderServicePort.OrderInfo(101L, 10L, "Pickup", "Delivery", "ORD-001")));

            RouteResponse result = useCase.execute(42L, routeId, request);

            assertThat(result.id()).isEqualTo(routeId);
            assertThat(result.status()).isEqualTo("En camino");
            assertThat(result.code()).isEqualTo("ORD-001");
        }

        @Test
        @DisplayName("TC4: ENTREGADO through the public API is rejected — it needs the customer's code")
        void entregadoThroughPublicApi_isRejectedAndNothingIsPersisted() {
            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("Entregado");

            assertThatThrownBy(() -> useCase.execute(42L, 1L, request))
                    .isInstanceOf(RouteDeliveryRequiresCodeException.class);

            verify(routeRepository, never()).updateStatus(any(), any());
        }

        @Test
        @DisplayName("TC2: invalid status string — throws IllegalArgumentException")
        void invalidStatusString_throwsIllegalArgumentException() {
            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("NOT_A_REAL_STATUS");

            assertThatThrownBy(() -> useCase.execute(42L, 1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status");
        }

        @Test
        @DisplayName("TC3: route not found — throws RouteNotFoundException")
        void routeNotFound_throwsRouteNotFoundException() {
            when(routeRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("ENTREGADO");

            assertThatThrownBy(() -> useCase.execute(42L, 999L, request))
                    .isInstanceOf(RouteNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}
