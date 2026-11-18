package com.flashdrop.reviews.infrastructure.adapter.outbound.client;

import com.flashdrop.reviews.application.port.outbound.OrderLookupPort;
import com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException;
import com.flashdrop.reviews.domain.exception.OrderNotFoundException;
import com.flashdrop.reviews.domain.model.OrderSnapshot;
import com.flashdrop.reviews.infrastructure.adapter.outbound.client.dto.InternalOrderSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador HTTP del puerto {@link OrderLookupPort} contra orders-service.
 *
 * <p>Endpoint: {@code GET /api/internal/orders/{id}}, autenticado con
 * {@code X-Internal-Api-Key} (cabecera por defecto del bean
 * {@code internalOrdersRestClient}).</p>
 *
 * <p>Degradación segura (fail-closed):</p>
 * <ul>
 *   <li>404 → {@link OrderNotFoundException} (la orden no existe).</li>
 *   <li>cualquier otro 4xx/5xx o fallo de red →
 *       {@link InternalServiceUnavailableException} (503). reviews-service
 *       nunca valida contra datos inventados.</li>
 * </ul>
 */
@Component
public class HttpOrderLookupClientAdapter implements OrderLookupPort {

    private static final Logger log = LoggerFactory.getLogger(HttpOrderLookupClientAdapter.class);
    private static final String SERVICE = "orders-service";

    private final RestClient restClient;

    public HttpOrderLookupClientAdapter(RestClient internalOrdersRestClient) {
        this.restClient = internalOrdersRestClient;
    }

    @Override
    public OrderSnapshot findOrder(Long orderId) {
        try {
            InternalOrderSummaryResponse response = restClient.get()
                    .uri("/api/internal/orders/{orderId}", orderId)
                    .retrieve()
                    .body(InternalOrderSummaryResponse.class);

            if (response == null) {
                throw new OrderNotFoundException(orderId);
            }
            return new OrderSnapshot(
                    response.orderId(),
                    response.status(),
                    response.clientId(),
                    response.restaurantId(),
                    response.courierUserId());
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("orders-service: la orden {} no existe", orderId);
            throw new OrderNotFoundException(orderId);
        } catch (RestClientResponseException ex) {
            log.error("orders-service devolvio {} para la orden {}: {}",
                    ex.getStatusCode().value(), orderId, ex.getResponseBodyAsString());
            throw new InternalServiceUnavailableException(SERVICE,
                    "respondio " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.error("orders-service no responde para la orden {}: {}", orderId, ex.getMessage());
            throw new InternalServiceUnavailableException(SERVICE, ex.getMessage(), ex);
        }
    }
}
