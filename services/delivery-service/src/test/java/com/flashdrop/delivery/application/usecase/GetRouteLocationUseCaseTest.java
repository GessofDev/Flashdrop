package com.flashdrop.delivery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.delivery.application.dto.RouteLocationResponse;
import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.CourierLocationNotFoundException;
import com.flashdrop.delivery.domain.exception.RouteAccessDeniedException;
import com.flashdrop.delivery.domain.exception.RouteNotFoundException;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

class GetRouteLocationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final CourierLocationStore locationStore = mock(CourierLocationStore.class);
    private final GetRouteLocationUseCase useCase = new GetRouteLocationUseCase(routeRepository, locationStore);

    private static DeliveryRoute route(String clientUserId, Long deliveryPersonId, RouteStatus status) {
        DeliveryRoute route = new DeliveryRoute();
        route.setId(100L);
        route.setClientUserId(clientUserId);
        route.setDeliveryPersonId(deliveryPersonId);
        route.setStatus(status);
        return route;
    }

    @Test
    void execute_owningClientActiveRouteAndFreshLocation_returnsLocation() {
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route("77", 10L, RouteStatus.EN_CAMINO)));
        when(locationStore.findByDeliveryPersonId(10L)).thenReturn(Optional.of(
                new CourierLocation(new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), NOW)));

        RouteLocationResponse response = useCase.execute("77", 100L);

        assertThat(response.routeId()).isEqualTo(100L);
        assertThat(response.latitude()).isEqualByComparingTo("-33.448900");
        assertThat(response.longitude()).isEqualByComparingTo("-70.669300");
        assertThat(response.recordedAt()).isEqualTo(NOW);
    }

    @Test
    void execute_differentClient_throwsRouteAccessDenied() {
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route("77", 10L, RouteStatus.EN_CAMINO)));

        assertThatThrownBy(() -> useCase.execute("88", 100L))
                .isInstanceOf(RouteAccessDeniedException.class);
    }

    @Test
    void execute_routeNotFound_throwsRouteNotFound() {
        when(routeRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("77", 100L))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void execute_terminalRoute_throwsLocationNotFound() {
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route("77", 10L, RouteStatus.ENTREGADO)));

        assertThatThrownBy(() -> useCase.execute("77", 100L))
                .isInstanceOf(CourierLocationNotFoundException.class);
    }

    @Test
    void execute_expiredOrMissingLocation_throwsLocationNotFound() {
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route("77", 10L, RouteStatus.EN_CAMINO)));
        when(locationStore.findByDeliveryPersonId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("77", 100L))
                .isInstanceOf(CourierLocationNotFoundException.class);
    }
}
