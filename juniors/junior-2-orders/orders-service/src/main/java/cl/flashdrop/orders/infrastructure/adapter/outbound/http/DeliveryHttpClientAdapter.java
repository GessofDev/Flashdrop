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
import org.springframework.web.client.RestClientException;

import java.util.*;

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

    // delivery-service (InternalRoutesController) expone PATCH
    // /api/internal/routes/order/{orderId}/status (GAP-01, resuelto en
    // commit 904464d de delivery-service). No existe variante bulk todavia,
    // asi que updateRouteStatus (usado por ClaimDeliveryOrdersUseCase para
    // varios pedidos a la vez) llama al endpoint single una vez por orderId.
    //
    // El status que ese endpoint acepta es el TOKEN del enum RouteStatus de
    // Delivery (PENDIENTE, ASSIGNED, RETIRAR_PEDIDO, EN_CAMINO, ENTREGADO),
    // no el texto en español que produce OrderStatus.getValue() en Orders
    // (ej. "Listo para retiro"). ORDER_STATUS_TO_ROUTE_TOKEN traduce entre
    // ambos vocabularios. "Nuevo pedido" y "Preparando" no tienen ruta
    // equivalente todavia (la ruta recien existe desde que el pedido esta
    // "Listo para retiro" en adelante) y se omiten sin llamar a Delivery.
    //
    // Cualquier falla de red o de contrato (404/400/5xx/timeout) se degrada
    // con gracia: se registra un WARN y se continua, para no tumbar la
    // transaccion de claim ni la de cambio de estado por un problema de
    // sincronizacion secundaria.
    private static final Map<String, String> ORDER_STATUS_TO_ROUTE_TOKEN = Map.of(
            "Listo para retiro", "RETIRAR_PEDIDO",
            "Retirado", "EN_CAMINO",
            "En camino", "EN_CAMINO",
            "Entregado", "ENTREGADO"
    );

    @Override
    public void updateRouteStatusByOrder(UUID orderId, String status) {
        long rawOrderId = IdConverter.toLong(orderId);
        String routeToken = ORDER_STATUS_TO_ROUTE_TOKEN.get(status);
        if (routeToken == null) {
            log.debug("updateRouteStatusByOrder: el estado de pedido '{}' no tiene ruta equivalente en "
                    + "Delivery; se omite sincronizacion (orderId={})", status, rawOrderId);
            return;
        }
        patchRouteStatus(rawOrderId, routeToken);
    }

    @Override
    public void updateRouteStatus(List<UUID> orderIds, String status) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        String routeToken = ORDER_STATUS_TO_ROUTE_TOKEN.get(status);
        if (routeToken == null) {
            log.debug("updateRouteStatus: el estado de pedido '{}' no tiene ruta equivalente en Delivery; "
                    + "se omite sincronizacion (orderIds={})", status, orderIds);
            return;
        }
        for (UUID orderId : orderIds) {
            patchRouteStatus(IdConverter.toLong(orderId), routeToken);
        }
    }

    /** No existe endpoint bulk en Delivery todavia (ver nota arriba): una llamada por orderId,
     *  cada una con su propio manejo de errores para que una falla no corte las demas. */
    private void patchRouteStatus(long rawOrderId, String routeToken) {
        log.debug("Sincronizando estado de ruta orderId={} -> {}", rawOrderId, routeToken);
        try {
            deliveryInternalRestClient.patch()
                    .uri("/api/internal/routes/order/{orderId}/status", rawOrderId)
                    .body(Map.of("status", routeToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("No se pudo sincronizar el estado de la ruta para orderId={} (status={}): {}",
                    rawOrderId, routeToken, e.getMessage());
        }
    }

    /** Forma real de la respuesta de GET /api/internal/delivery-persons (ApiResponse<DeliveryPersonResponse>). */
    private record DeliveryPersonEnvelope(boolean success, String message, DeliveryPersonData data) {
    }

    private record DeliveryPersonData(Long id, String userId, String vehicle) {
    }
}
