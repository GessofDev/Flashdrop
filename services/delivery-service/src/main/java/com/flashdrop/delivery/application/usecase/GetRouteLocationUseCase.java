package com.flashdrop.delivery.application.usecase;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.flashdrop.delivery.application.dto.RouteLocationResponse;
import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.CourierLocationNotFoundException;
import com.flashdrop.delivery.domain.exception.RouteAccessDeniedException;
import com.flashdrop.delivery.domain.exception.RouteNotFoundException;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

@Service
public class GetRouteLocationUseCase {

    private static final Set<RouteStatus> ACTIVE_STATUSES = EnumSet.of(
            RouteStatus.ASSIGNED,
            RouteStatus.RETIRAR_PEDIDO,
            RouteStatus.EN_CAMINO
    );

    private final RouteRepository routeRepository;
    private final CourierLocationStore locationStore;

    public GetRouteLocationUseCase(
            RouteRepository routeRepository,
            CourierLocationStore locationStore
    ) {
        this.routeRepository = routeRepository;
        this.locationStore = locationStore;
    }

    public RouteLocationResponse execute(String clientUserId, Long routeId) {
        DeliveryRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException(routeId));

        if (route.getClientUserId() == null || !route.getClientUserId().equals(clientUserId)) {
            throw new RouteAccessDeniedException();
        }

        if (route.getDeliveryPersonId() == null
                || route.getStatus() == null
                || !ACTIVE_STATUSES.contains(route.getStatus())) {
            throw new CourierLocationNotFoundException(routeId);
        }

        CourierLocation location = locationStore.findByDeliveryPersonId(route.getDeliveryPersonId())
                .orElseThrow(() -> new CourierLocationNotFoundException(routeId));

        return new RouteLocationResponse(
                route.getId(),
                location.latitude(),
                location.longitude(),
                location.recordedAt()
        );
    }
}
