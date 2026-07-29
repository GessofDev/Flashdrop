package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateRouteStatusUseCaseImplTest {

    @Mock
    private RouteRepository routeRepository;

    private UpdateRouteStatusUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateRouteStatusUseCaseImpl(routeRepository);
    }

    @Nested
    @DisplayName("execute(Long routeId, UpdateRouteStatusRequest)")
    class Execute {

        @Test
        @DisplayName("TC1: valid status update — returns updated RouteResponse")
        void validStatusUpdate_returnsUpdatedRouteResponse() {
            Long routeId = 1L;
            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("ENTREGADO");
            DeliveryRoute updated = new DeliveryRoute(routeId, 101L, "Pickup", "Delivery",
                    Distance.of(new BigDecimal("3.5")), EstimatedTime.of(20),
                    RouteStatus.ENTREGADO, Instant.now());

            when(routeRepository.updateStatus(eq(routeId), any())).thenReturn(updated);

            RouteResponse result = useCase.execute(routeId, request);

            assertThat(result.id()).isEqualTo(routeId);
            assertThat(result.status()).isEqualTo("ENTREGADO");
        }

        @Test
        @DisplayName("TC2: invalid status string — throws IllegalArgumentException")
        void invalidStatusString_throwsIllegalArgumentException() {
            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("NOT_A_REAL_STATUS");

            assertThatThrownBy(() -> useCase.execute(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status");
        }

        @Test
        @DisplayName("TC3: route not found — throws RouteNotFoundException")
        void routeNotFound_throwsRouteNotFoundException() {
            when(routeRepository.updateStatus(eq(999L), any()))
                    .thenThrow(new RouteNotFoundException(999L));

            UpdateRouteStatusRequest request = new UpdateRouteStatusRequest("ENTREGADO");

            assertThatThrownBy(() -> useCase.execute(999L, request))
                    .isInstanceOf(RouteNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}
