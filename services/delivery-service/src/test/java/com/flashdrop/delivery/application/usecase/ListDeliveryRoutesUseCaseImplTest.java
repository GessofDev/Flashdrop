package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListDeliveryRoutesUseCaseImplTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private OrderServicePort orderServicePort;

    private ListDeliveryRoutesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListDeliveryRoutesUseCaseImpl(routeRepository, orderServicePort);
    }

    private DeliveryRoute newRoute(Long id, Long orderId, Long deliveryPersonId, String pickup, String delivery) {
        return new DeliveryRoute(id, orderId, deliveryPersonId, pickup, delivery,
                Distance.of(new BigDecimal("3.5")),
                EstimatedTime.of(20),
                RouteStatus.PENDIENTE, Instant.now());
    }

    @Nested
    @DisplayName("execute(Long deliveryPersonId)")
    class Execute {

        @Test
        @DisplayName("filterByDeliveryPersonId_returnsOnlyMatchingRoutes")
        void filterByDeliveryPersonId_returnsOnlyMatchingRoutes() {
            DeliveryRoute routeA1 = newRoute(1L, 101L, 7L, "PickupA1", "DeliveryA1");
            DeliveryRoute routeA2 = newRoute(2L, 102L, 7L, "PickupA2", "DeliveryA2");
            DeliveryRoute routeB = newRoute(3L, 103L, 99L, "PickupB", "DeliveryB");
            when(routeRepository.findByDeliveryPersonId(7L)).thenReturn(List.of(routeA1, routeA2));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L)))
                    .thenReturn(List.of(
                            new OrderServicePort.OrderInfo(101L, 10L, "PickupA1", "DeliveryA1", "ORD-001"),
                            new OrderServicePort.OrderInfo(102L, 10L, "PickupA2", "DeliveryA2", "ORD-002")));

            List<RouteResponse> result = useCase.execute(7L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(1).id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("nullDeliveryPersonId_throwsIllegalArgumentException")
        void nullDeliveryPersonId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> useCase.execute(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("emptyDatabase_returnsEmptyList")
        void emptyDatabase_returnsEmptyList() {
            when(routeRepository.findByDeliveryPersonId(7L)).thenReturn(Collections.emptyList());

            List<RouteResponse> result = useCase.execute(7L);

            assertThat(result).isEmpty();
        }
    }
}
