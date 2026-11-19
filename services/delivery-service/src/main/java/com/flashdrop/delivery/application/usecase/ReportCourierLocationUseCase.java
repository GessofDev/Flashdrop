package com.flashdrop.delivery.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.NoActiveRouteException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

@Service
public class ReportCourierLocationUseCase {

    static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(5);
    private static final Set<RouteStatus> ACTIVE_STATUSES = EnumSet.of(
            RouteStatus.ASSIGNED,
            RouteStatus.RETIRAR_PEDIDO,
            RouteStatus.EN_CAMINO
    );

    private final DeliveryPersonRepository deliveryPersonRepository;
    private final RouteRepository routeRepository;
    private final CourierLocationStore locationStore;
    private final Clock clock;

    @Autowired
    public ReportCourierLocationUseCase(
            DeliveryPersonRepository deliveryPersonRepository,
            RouteRepository routeRepository,
            CourierLocationStore locationStore
    ) {
        this(deliveryPersonRepository, routeRepository, locationStore, Clock.systemUTC());
    }

    ReportCourierLocationUseCase(
            DeliveryPersonRepository deliveryPersonRepository,
            RouteRepository routeRepository,
            CourierLocationStore locationStore,
            Clock clock
    ) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.routeRepository = routeRepository;
        this.locationStore = locationStore;
        this.clock = clock;
    }

    public void execute(String userId, BigDecimal latitude, BigDecimal longitude, Instant recordedAt) {
        CourierLocation candidate = new CourierLocation(latitude, longitude, recordedAt);

        Instant latestAllowed = Instant.now(clock).plus(MAX_FUTURE_SKEW);
        if (recordedAt.isAfter(latestAllowed)) {
            throw new IllegalArgumentException("recordedAt cannot be in the future");
        }

        DeliveryPerson person = deliveryPersonRepository.findByUserId(userId)
                .orElseThrow(() -> new DeliveryPersonNotFoundException(
                        "Delivery person not found for userId: " + userId));

        boolean hasActiveRoute = routeRepository.findByDeliveryPersonId(person.getId()).stream()
                .anyMatch(route -> route.getStatus() != null && ACTIVE_STATUSES.contains(route.getStatus()));
        if (!hasActiveRoute) {
            throw new NoActiveRouteException(userId);
        }

        locationStore.findByDeliveryPersonId(person.getId()).ifPresent(existing -> {
            if (recordedAt.isBefore(existing.recordedAt())) {
                throw new IllegalArgumentException("recordedAt is older than the last stored location");
            }
        });

        locationStore.save(person.getId(), candidate);
    }
}
