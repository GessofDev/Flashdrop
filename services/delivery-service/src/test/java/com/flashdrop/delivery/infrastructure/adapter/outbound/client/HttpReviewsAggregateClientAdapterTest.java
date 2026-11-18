package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.flashdrop.delivery.application.port.outbound.RatingAggregate;
import com.flashdrop.delivery.domain.exception.RatingAggregatesUnavailableException;
import com.flashdrop.delivery.domain.model.RatingTargetType;

/**
 * Contrato HTTP con reviews-service (T15).
 *
 * <p>Se fija el metodo, la ruta, los query params y el header
 * {@code X-Internal-Api-Key}, que es lo que reviews-service valida.
 */
@DisplayName("HttpReviewsAggregateClientAdapter — GET /api/internal/reviews/aggregates")
class HttpReviewsAggregateClientAdapterTest {

    private MockRestServiceServer server;
    private HttpReviewsAggregateClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reviews-service:8085");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpReviewsAggregateClientAdapter(builder.build(), "test-internal-key");
    }

    @Test
    @DisplayName("pide los agregados con el header de API interna y los devuelve indexados por id")
    void fetchAggregates_callsTheInternalEndpointAndMapsTheResponse() {
        server.expect(requestTo(startsWith("http://reviews-service:8085/api/internal/reviews/aggregates")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(queryParam("targetType", "REPARTIDOR"))
                .andExpect(queryParam("ids", "33,44"))
                .andRespond(withSuccess("""
                        {"items":[{"targetId":33,"average":4.5,"count":12},{"targetId":44,"average":3.0,"count":4}]}
                        """, MediaType.APPLICATION_JSON));

        Map<Long, RatingAggregate> aggregates =
                adapter.fetchAggregates(RatingTargetType.REPARTIDOR, List.of(33L, 44L));

        assertThat(aggregates)
                .containsEntry(33L, new RatingAggregate(33L, 4.5, 12L))
                .containsEntry(44L, new RatingAggregate(44L, 3.0, 4L));
        server.verify();
    }

    @Test
    @DisplayName("sin ids no hay llamada al servicio")
    void fetchAggregates_withoutIds_doesNotCallTheService() {
        assertThat(adapter.fetchAggregates(RatingTargetType.REPARTIDOR, List.of())).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("una respuesta 5xx se traduce a un fallo explicito, no a un promedio vacio")
    void fetchAggregates_whenTheServiceFails_raisesUnavailable() {
        server.expect(requestTo(startsWith("http://reviews-service:8085/api/internal/reviews/aggregates")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchAggregates(RatingTargetType.REPARTIDOR, List.of(33L)))
                .isInstanceOf(RatingAggregatesUnavailableException.class);
    }
}
