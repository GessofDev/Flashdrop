package com.flashdrop.delivery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.NoActiveRouteException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

class ReportCourierLocationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private final DeliveryPersonRepository personRepository = mock(DeliveryPersonRepository.class);
    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final InMemoryFakeStore locationStore = new InMemoryFakeStore();
    private final ReportCourierLocationUseCase useCase = new ReportCourierLocationUseCase(
            personRepository, routeRepository, locationStore, Clock.fixed(NOW, ZoneOffset.UTC));

    private static DeliveryPerson courier() {
        return new DeliveryPerson(10L, "50", null, NOW);
    }

    private static DeliveryRoute routeWithStatus(RouteStatus status) {
        DeliveryRoute route = new DeliveryRoute();
        route.setId(1L);
        route.setDeliveryPersonId(10L);
        route.setStatus(status);
        return route;
    }

    @Test
    void execute_activeRouteAndFreshLocation_savesLocation() {
        when(personRepository.findByUserId("50")).thenReturn(Optional.of(courier()));
        when(routeRepository.findByDeliveryPersonId(10L)).thenReturn(List.of(routeWithStatus(RouteStatus.EN_CAMINO)));

        useCase.execute("50", new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), NOW.minusSeconds(2));

        assertThat(locationStore.findByDeliveryPersonId(10L)).isPresent();
        assertThat(locationStore.findByDeliveryPersonId(10L).get().latitude()).isEqualByComparingTo("-33.448900");
    }

    @Test
    void execute_noActiveRoute_throwsNoActiveRouteException() {
        when(personRepository.findByUserId("50")).thenReturn(Optional.of(courier()));
        when(routeRepository.findByDeliveryPersonId(10L)).thenReturn(List.of(routeWithStatus(RouteStatus.ENTREGADO)));

        assertThatThrownBy(() -> useCase.execute(
                "50", new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), NOW))
                .isInstanceOf(NoActiveRouteException.class);
    }

    @Test
    void execute_unknownCourier_throwsDeliveryPersonNotFound() {
        when(personRepository.findByUserId("99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                "99", new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), NOW))
                .isInstanceOf(DeliveryPersonNotFoundException.class);
    }

    @Test
    void execute_futureRecordedAtBeyondSkew_rejected() {
        assertThatThrownBy(() -> useCase.execute(
                "50", new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), NOW.plusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_olderThanStoredRecordedAt_rejected() {
        when(personRepository.findByUserId("50")).thenReturn(Optional.of(courier()));
        when(routeRepository.findByDeliveryPersonId(10L)).thenReturn(List.of(routeWithStatus(RouteStatus.ASSIGNED)));
        locationStore.save(10L, new CourierLocation(new BigDecimal("-33.44"), new BigDecimal("-70.66"), NOW.minusSeconds(5)));

        assertThatThrownBy(() -> useCase.execute(
                "50", new BigDecimal("-33.45"), new BigDecimal("-70.67"), NOW.minusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class InMemoryFakeStore implements CourierLocationStore {
        private final Map<Long, CourierLocation> store = new HashMap<>();

        @Override
        public void save(Long deliveryPersonId, CourierLocation location) {
            store.put(deliveryPersonId, location);
        }

        @Override
        public Optional<CourierLocation> findByDeliveryPersonId(Long deliveryPersonId) {
            return Optional.ofNullable(store.get(deliveryPersonId));
        }

        @Override
        public void deleteByDeliveryPersonId(Long deliveryPersonId) {
            store.remove(deliveryPersonId);
        }
    }
}
