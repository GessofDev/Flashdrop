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

    private DeliveryRoute newRoute(Long id, Long orderId, String pickup, String delivery) {
        return new DeliveryRoute(id, orderId, pickup, delivery,
                Distance.of(new BigDecimal("3.5")),
                EstimatedTime.of(20),
                RouteStatus.PENDIENTE, Instant.now());
    }

    @Nested
    @DisplayName("execute(Long deliveryPersonId)")
    class Execute {

        @Test
        @DisplayName("TC1: null deliveryPersonId — returns all routes with code from OrderServicePort")
        void nullDeliveryPersonId_returnsAllRoutes() {
            DeliveryRoute route = newRoute(1L, 101L, "Pickup", "Delivery");
            when(routeRepository.findAll()).thenReturn(List.of(route));
            when(orderServicePort.getOrdersByIds(List.of(101L)))
                    .thenReturn(List.of(new OrderServicePort.OrderInfo(101L, 10L, "Pickup", "Delivery", "ORD-001")));

            List<RouteResponse> result = useCase.execute(null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).code()).isEqualTo("ORD-001");
        }

        @Test
        @DisplayName("TC2: deliveryPersonId provided — param is ignored, returns all routes")
        void withDeliveryPersonId_returnsAllRoutes() {
            DeliveryRoute route = newRoute(2L, 102L, "Pickup2", "Delivery2");
            when(routeRepository.findAll()).thenReturn(List.of(route));
            when(orderServicePort.getOrdersByIds(List.of(102L)))
                    .thenReturn(List.of(new OrderServicePort.OrderInfo(102L, 10L, "Pickup2", "Delivery2", "ORD-002")));

            List<RouteResponse> result = useCase.execute(42L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(2L);
            assertThat(result.get(0).code()).isEqualTo("ORD-002");
        }

        @Test
        @DisplayName("TC3: empty DB — returns empty list")
        void emptyDatabase_returnsEmptyList() {
            when(routeRepository.findAll()).thenReturn(Collections.emptyList());

            List<RouteResponse> result = useCase.execute(null);

            assertThat(result).isEmpty();
        }
    }
}
