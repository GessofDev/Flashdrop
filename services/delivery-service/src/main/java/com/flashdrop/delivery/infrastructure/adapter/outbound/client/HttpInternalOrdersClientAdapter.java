package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.InternalOrdersClientPort;
import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP adapter that delegates the claim to orders-service.
 *
 * <p>Endpoint: {@code POST /api/internal/orders/claim}. Auth:
 * {@code X-Internal-Api-Key} (default header on the injected
 * {@link RestClient} bean — see
 * {@link com.flashdrop.delivery.infrastructure.config.InternalOrdersRestClientConfig}).
 * Wire body (plan D5): {@code {"userId": <long>, "orderIds": [<long>, ...]}}.
 *
 * <p><b>PR-B invariant</b>: throws {@link OrderClaimFailedException} on EVERY
 * non-2xx response. We do NOT replicate the swallow-4xx/5xx anti-pattern from
 * {@code HttpOrderServiceClientAdapter.java:84-88} (separate delivery-side bug,
 * out of scope).
 *
 * <p>Status mapping:
 * <ul>
 *   <li>2xx → success (no exception)</li>
 *   <li>4xx (e.g. 409 Conflict) → preserve upstream {@link HttpStatus}</li>
 *   <li>5xx → map to {@link HttpStatus#SERVICE_UNAVAILABLE} (orders is degraded)</li>
 *   <li>{@link ResourceAccessException} (network failure) → {@link HttpStatus#SERVICE_UNAVAILABLE}</li>
 * </ul>
 */
@Component
public class HttpInternalOrdersClientAdapter implements InternalOrdersClientPort {

    private static final Logger log = LoggerFactory.getLogger(HttpInternalOrdersClientAdapter.class);

    private final RestClient restClient;

    public HttpInternalOrdersClientAdapter(RestClient internalOrdersRestClient) {
        this.restClient = internalOrdersRestClient;
    }

    @Override
    public void claimOrders(Long userId, List<Long> orderIds) {
        // LinkedHashMap locks the field order to {userId, orderIds} for a stable wire
        // contract — Map.of() does not guarantee insertion order on JVMs with hash-randomized
        // maps, which would make contract tests flaky.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("orderIds", orderIds);

        try {
            restClient.post()
                    .uri("/api/internal/orders/claim")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // 4xx — preserve upstream status. The body carries the upstream's error message.
            log.error("orders-service /api/internal/orders/claim returned {} (userId={}, orderIds={}): {}",
                    ex.getStatusCode(), userId, orderIds, ex.getResponseBodyAsString());
            throw new OrderClaimFailedException(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    extractMessage(ex),
                    ex);
        } catch (HttpServerErrorException ex) {
            // 5xx — orders-service is degraded. Map to SERVICE_UNAVAILABLE so the
            // client gets a clear "try again later" rather than mirroring the
            // exact upstream 5xx status (which is a leaky abstraction).
            log.error("orders-service /api/internal/orders/claim returned {} (userId={}, orderIds={}): {}",
                    ex.getStatusCode(), userId, orderIds, ex.getResponseBodyAsString());
            throw new OrderClaimFailedException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "orders-service is degraded: " + extractMessage(ex),
                    ex);
        } catch (ResourceAccessException ex) {
            // Network failure (connection refused, timeout, DNS, etc.).
            log.error("orders-service /api/internal/orders/claim unreachable (userId={}, orderIds={}): {}",
                    userId, orderIds, ex.getMessage());
            throw new OrderClaimFailedException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "orders-service is unreachable: " + ex.getMessage(),
                    ex);
        }
    }

    private static String extractMessage(HttpClientErrorException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : ex.getStatusText();
    }

    private static String extractMessage(HttpServerErrorException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : ex.getStatusText();
    }
}
