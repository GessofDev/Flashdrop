package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Contract verification for {@link HttpOrderServiceClientAdapter}.
 *
 * <p>Locks the HTTP contract to MIGRATION_PLAN.md §8.3:
 * <ul>
 *   <li>Path: {@code GET /api/internal/orders?ids=...}</li>
 *   <li>Auth header: {@code X-Internal-Api-Key}</li>
 *   <li>ID type: {@code long} (NOT UUID — that was an earlier PR-5 mistake).</li>
 * </ul>
 *
 * <p>If this test fails, someone changed the path/header/ID semantics.
 */
class HttpOrderServiceContractTest {

    @Test
    @DisplayName("Calls /api/internal/orders with X-Internal-Api-Key and parses long ids")
    void callsInternalOrdersEndpointWithApiKey() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://orders-service:8083")
                .defaultHeader("X-Internal-Api-Key", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://orders-service:8083/api/internal/orders")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andRespond(withSuccess(
                        """
                        [{"id":101,"clientId":7,"restaurantId":10,"deliveryId":null,"status":"Preparando","address":"Calle 1","code":"ORD-001"}]
                        """,
                        APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.equalTo("http://orders-service:8083/api/internal/restaurants/10")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"id":10,"userId":7,"name":"Urban Burger","address":"Av. Providencia 1200"}
                        """,
                        APPLICATION_JSON));

        HttpOrderServiceClientAdapter adapter = new HttpOrderServiceClientAdapter(builder.build());

        List<com.flashdrop.delivery.application.port.outbound.OrderServicePort.OrderInfo> result =
                adapter.getOrdersByIds(List.of(101L));

        server.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
        assertThat(result.get(0).restaurantId()).isEqualTo(10L);
        assertThat(result.get(0).pickupAddress()).contains("Urban Burger");
    }

    @Test
    @DisplayName("Graceful degradation: 404 from orders-service returns empty list without throwing")
    void gracefulDegradationOn404() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://orders-service:8083")
                .defaultHeader("X-Internal-Api-Key", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://orders-service:8083/api/internal/orders")))
                .andRespond(withSuccess("[]", APPLICATION_JSON));

        HttpOrderServiceClientAdapter adapter = new HttpOrderServiceClientAdapter(builder.build());

        List<com.flashdrop.delivery.application.port.outbound.OrderServicePort.OrderInfo> result =
                adapter.getOrdersByIds(List.of(101L));

        server.verify();
        assertThat(result).isEmpty();
    }
}
