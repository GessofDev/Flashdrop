package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
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

    private ListDeliveryRoutesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListDeliveryRoutesUseCaseImpl(routeRepository);
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
        @DisplayName("TC1: null deliveryPersonId — returns all routes")
        void nullDeliveryPersonId_returnsAllRoutes() {
            when(routeRepository.findAll()).thenReturn(List.of(newRoute(1L, 101L, "Pickup", "Delivery")));

            List<RouteResponse> result = useCase.execute(null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("TC2: deliveryPersonId provided — param is ignored, returns all routes")
        void withDeliveryPersonId_returnsAllRoutes() {
            when(routeRepository.findAll()).thenReturn(List.of(newRoute(2L, 102L, "Pickup2", "Delivery2")));

            List<RouteResponse> result = useCase.execute(42L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(2L);
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
