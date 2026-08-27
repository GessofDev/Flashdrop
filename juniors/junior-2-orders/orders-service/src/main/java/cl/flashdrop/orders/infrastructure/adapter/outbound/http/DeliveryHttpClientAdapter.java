package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalDeliveryPersonDto;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cliente HTTP hacia Delivery Service (contratos C-5, C-6, C-7).
 *
 * <p>Orders ya no accede directamente a las tablas {@code delivery} ni
 * {@code delivery_routes}; las rutas y los perfiles de reparto se resuelven
 * vía API interna de delivery-service.</p>
 */
@Component
@RequiredArgsConstructor
public class DeliveryHttpClientAdapter implements DeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryHttpClientAdapter.class);
    static final String SERVICE = "Delivery";

    private final RestClient deliveryInternalRestClient;

    @Override
    public Optional<UUID> findDeliveryIdByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long uid = IdConverter.toLong(userId);
        log.debug("Consultando repartidor interno por userId={}", uid);
        try {
            InternalDeliveryPersonDto dto = deliveryInternalRestClient.get()
                    .uri("/api/internal/delivery/by-user/{userId}", uid)
                    .retrieve()
                    .body(InternalDeliveryPersonDto.class);
            if (dto == null) {
                return Optional.empty();
            }
            return Optional.of(IdConverter.toUuid(dto.id()));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    @Override
    public void saveRoute(DeliveryRoute route) {
        log.debug("Creando ruta interna para orderId={}", route.getOrderId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", IdConverter.toLongParam(route.getOrderId()));
        body.put("pickupAddress", route.getPickupAddress());
        body.put("deliveryAddress", route.getDeliveryAddress());
        body.put("distanceKm", route.getDistanceKm());
        body.put("estimatedMinutes", route.getEstimatedMinutes());
        body.put("status", route.getStatus());
        try {
            deliveryInternalRestClient.post()
                    .uri("/api/internal/delivery/routes")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    @Override
    public void updateRouteStatusByOrder(UUID orderId, String status) {
        long rawOrderId = IdConverter.toLong(orderId);
        log.debug("Sincronizando estado de ruta por order={}", rawOrderId);
        try {
            deliveryInternalRestClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/delivery/routes/order/{orderId}")
                            .queryParam("status", status)
                            .build(rawOrderId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    @Override
    public void updateRouteStatus(List<UUID> orderIds, String status) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        log.debug("Sincronizando estado de ruta bulk orderIds={}", rawIds);
        try {
            deliveryInternalRestClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/delivery/routes")
                            .queryParam("status", status)
                            .build())
                    .body(rawIds)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }
}
