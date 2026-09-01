package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
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
            // delivery-service envuelve la respuesta en {success, message, data:{id,...}}
            DeliveryPersonEnvelope envelope = deliveryInternalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/delivery-persons")
                            .queryParam("userId", uid)
                            .build())
                    .retrieve()
                    .body(DeliveryPersonEnvelope.class);
            if (envelope == null || envelope.data() == null || envelope.data().id() == null) {
                return Optional.empty();
            }
            return Optional.of(IdConverter.toUuid(envelope.data().id()));
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
                    .uri("/api/internal/routes")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    // NOTA: delivery-service (InternalRoutesController) sólo expone
    // PATCH /api/internal/routes/{routeId}/status (por routeId, uno a la vez).
    // No existe todavía un endpoint "por orderId" ni "bulk" como el que estos
    // dos métodos necesitarían para llamar en una sola pasada. Mientras ese
    // contrato no se defina en Delivery, se degrada con gracia (log + sigue)
    // en vez de tumbar la transacción de claim completa, igual que hace
    // HttpOrderServiceClientAdapter del lado de Delivery.

    @Override
    public void updateRouteStatusByOrder(UUID orderId, String status) {
        long rawOrderId = IdConverter.toLong(orderId);
        log.warn("updateRouteStatusByOrder: no existe endpoint por orderId en delivery-service todavia "
                + "(orderId={}, status={}); se omite la sincronizacion de ruta", rawOrderId, status);
    }

    @Override
    public void updateRouteStatus(List<UUID> orderIds, String status) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        log.warn("updateRouteStatus: no existe endpoint bulk por orderIds en delivery-service todavia "
                + "(orderIds={}, status={}); se omite la sincronizacion de ruta", rawIds, status);
    }

    /** Forma real de la respuesta de GET /api/internal/delivery-persons (ApiResponse<DeliveryPersonResponse>). */
    private record DeliveryPersonEnvelope(boolean success, String message, DeliveryPersonData data) {
    }

    private record DeliveryPersonData(Long id, String userId, String vehicle) {
    }
}
