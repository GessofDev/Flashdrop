package com.flashdrop.reviews.infrastructure.adapter.outbound.client;

import com.flashdrop.reviews.application.port.outbound.RestaurantLookupPort;
import com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException;
import com.flashdrop.reviews.infrastructure.adapter.outbound.client.dto.InternalRestaurantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Adaptador HTTP del puerto {@link RestaurantLookupPort} contra catalog-service.
 *
 * <p>Endpoint: {@code GET /api/internal/restaurants?userId={userId}}, autenticado
 * con {@code X-Internal-Api-Key}.</p>
 *
 * <p>Degradación segura (fail-closed): si catalog-service no responde, la
 * excepción sube hasta el manejador global y la petición termina en 503 — un
 * dueño de tienda no puede calificar si no se puede verificar de qué tienda
 * es dueño.</p>
 */
@Component
public class HttpRestaurantLookupClientAdapter implements RestaurantLookupPort {

    private static final Logger log = LoggerFactory.getLogger(HttpRestaurantLookupClientAdapter.class);
    private static final String SERVICE = "catalog-service";

    private final RestClient restClient;

    public HttpRestaurantLookupClientAdapter(RestClient internalCatalogRestClient) {
        this.restClient = internalCatalogRestClient;
    }

    @Override
    public Optional<Long> findRestaurantIdByOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        try {
            InternalRestaurantResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/restaurants")
                            .queryParam("userId", ownerUserId)
                            .build())
                    .retrieve()
                    .body(InternalRestaurantResponse.class);

            if (response == null || response.id() == null) {
                return Optional.empty();
            }
            return Optional.of(response.id());
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("catalog-service: el usuario {} no tiene restaurante", ownerUserId);
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            log.error("catalog-service devolvio {} para el usuario {}: {}",
                    ex.getStatusCode().value(), ownerUserId, ex.getResponseBodyAsString());
            throw new InternalServiceUnavailableException(SERVICE,
                    "respondio " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.error("catalog-service no responde para el usuario {}: {}", ownerUserId, ex.getMessage());
            throw new InternalServiceUnavailableException(SERVICE, ex.getMessage(), ex);
        }
    }
}
