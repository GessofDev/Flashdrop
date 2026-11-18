package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.flashdrop.delivery.application.port.outbound.RatingAggregateClientPort;
import com.flashdrop.delivery.domain.exception.RatingAggregatesUnavailableException;
import com.flashdrop.delivery.domain.model.RatingTargetType;
import com.flashdrop.delivery.application.port.outbound.RatingAggregate;

/**
 * Adapter HTTP del endpoint interno de agregados de reviews-service.
 *
 * <p>Contrato: {@code GET /api/internal/reviews/aggregates?targetType=REPARTIDOR&ids=1,2,3}
 * con header {@code X-Internal-Api-Key}. La respuesta es
 * {@code {"items":[{"targetId":1,"average":4.5,"count":12}]}}.
 *
 * <p>Cuando el servicio no responde se lanza
 * {@link RatingAggregatesUnavailableException}: el caso de uso conserva el
 * ultimo valor cacheado en lugar de inventar un promedio.
 */
@Component
public class HttpReviewsAggregateClientAdapter implements RatingAggregateClientPort {

    private static final String AGGREGATES_PATH = "/api/internal/reviews/aggregates";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public HttpReviewsAggregateClientAdapter(
            RestClient reviewsServiceRestClient,
            @org.springframework.beans.factory.annotation.Value("${delivery.reviews.api-key:${internal.api.key:}}") String internalApiKey
    ) {
        this.restClient = reviewsServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Map<Long, RatingAggregate> fetchAggregates(RatingTargetType targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }

        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new RatingAggregatesUnavailableException(
                    "No hay clave interna configurada para llamar a reviews-service "
                            + "(delivery.reviews.api-key / internal.api.key)", null);
        }

        String ids = targetIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        AggregatesResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(AGGREGATES_PATH)
                            .queryParam("targetType", targetType.name())
                            .queryParam("ids", ids)
                            .build())
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(AggregatesResponse.class);
        } catch (RestClientException exception) {
            throw new RatingAggregatesUnavailableException(
                    "reviews-service no respondio el pedido de agregados de " + targetType, exception);
        }

        if (response == null || response.items() == null) {
            return Map.of();
        }

        Map<Long, RatingAggregate> aggregates = new LinkedHashMap<>();
        for (AggregateItem item : response.items()) {
            if (item != null && item.targetId() != null) {
                aggregates.put(item.targetId(), new RatingAggregate(
                        item.targetId(),
                        item.average(),
                        item.count() == null ? 0L : item.count()));
            }
        }
        return aggregates;
    }

    /** Envoltura de la respuesta de reviews-service. */
    record AggregatesResponse(List<AggregateItem> items) {
    }

    /** Un destino dentro de la respuesta de reviews-service. */
    record AggregateItem(Long targetId, Double average, Long count) {
    }
}
