package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contract verification for {@link HttpInternalOrdersClientAdapter}.
 *
 * <p>Locks the wire contract from the plan:
 * <ul>
 *   <li>Path: {@code POST /api/internal/orders/claim} on {@code orders-service:8083}</li>
 *   <li>Auth header: {@code X-Internal-Api-Key}</li>
 *   <li>Body: {@code {"userId": <long>, "orderIds": [<long>, ...]}}</li>
 *   <li>2xx → success (no exception)</li>
 *   <li>409 → {@link OrderClaimFailedException} with {@code CONFLICT} status</li>
 *   <li>5xx / network failure → {@link OrderClaimFailedException} with {@code SERVICE_UNAVAILABLE} status</li>
 * </ul>
 *
 * <p><b>PR-B invariant</b>: this adapter MUST throw on every non-2xx response. The
 * existing {@link HttpOrderServiceClientAdapter} swallows 4xx/5xx at lines 84-88 — a
 * separate delivery-side bug, out of scope. Do not replicate.
 */
class HttpInternalOrdersClientAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = "http://orders-service:8083";
    private static final String ENDPOINT = BASE_URL + "/api/internal/orders/claim";
    private static final String API_KEY = "test-key";

    @Nested
    @DisplayName("claimOrders(userId, orderIds) — happy path")
    class HappyPath {

        @Test
        @DisplayName("TC1: 200 OK — no exception; correct path, method, header, and body shape")
        void twoHundred_returnsNormallyAndSendsCorrectRequest() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            String expectedBody = "{\"userId\":42,\"orderIds\":[101,102]}";
            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Internal-Api-Key", API_KEY))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().string(expectedBody))
                    .andRespond(withSuccess("{\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            adapter.claimOrders(42L, List.of(101L, 102L));

            server.verify();
        }

        @Test
        @DisplayName("TC2: empty success body is still treated as success (no exception)")
        void twoHundredWithEmptyBody_returnsNormally() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            adapter.claimOrders(1L, List.of(10L));

            server.verify();
        }
    }

    @Nested
    @DisplayName("claimOrders(userId, orderIds) — non-2xx responses throw OrderClaimFailedException")
    class Non2xxResponses {

        @Test
        @DisplayName("TC1: 409 Conflict — CONFLICT status + upstream message preserved")
        void conflict_throwsWithConflictStatusAndMessage() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            String upstreamBody = "Order already claimed by another courier";
            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.CONFLICT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(upstreamBody));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            assertThatThrownBy(() -> adapter.claimOrders(42L, List.of(101L)))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .hasMessageContaining("Order already claimed")
                    .extracting(e -> ((OrderClaimFailedException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("TC2: 503 Service Unavailable — SERVICE_UNAVAILABLE status")
        void serviceUnavailable_throwsWithServiceUnavailableStatus() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("upstream down"));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            assertThatThrownBy(() -> adapter.claimOrders(42L, List.of(101L)))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .extracting(e -> ((OrderClaimFailedException) e).getStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("TC3: 500 Internal Server Error — maps to SERVICE_UNAVAILABLE")
        void internalServerError_mapsToServiceUnavailable() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("unexpected"));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            assertThatThrownBy(() -> adapter.claimOrders(42L, List.of(101L)))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .extracting(e -> ((OrderClaimFailedException) e).getStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("TC4: 400 Bad Request — BAD_REQUEST status preserved")
        void badRequest_throwsWithBadRequestStatus() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("invalid orderIds"));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            assertThatThrownBy(() -> adapter.claimOrders(42L, List.of(101L)))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .extracting(e -> ((OrderClaimFailedException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("claimOrders(userId, orderIds) — network failure maps to SERVICE_UNAVAILABLE")
    class NetworkFailure {

        @Test
        @DisplayName("TC1: IOException at the request factory — RestClient wraps it as ResourceAccessException, adapter maps to SERVICE_UNAVAILABLE + cause preserved")
        void connectionRefused_throwsServiceUnavailableWithCause() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            // MockRestResponseCreators.withException(IOException) — when the underlying
            // ClientHttpRequestFactory throws an IOException, Spring's RestClient wraps
            // it as ResourceAccessException before the adapter sees it.
            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withException(new IOException("Connection refused")));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            assertThatThrownBy(() -> adapter.claimOrders(42L, List.of(101L)))
                    .isInstanceOf(OrderClaimFailedException.class)
                    .hasCauseInstanceOf(ResourceAccessException.class)
                    .extracting(e -> ((OrderClaimFailedException) e).getStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("claimOrders(userId, orderIds) — request body shape")
    class RequestBodyShape {

        @Test
        @DisplayName("TC1: body contains the userId as a long and the orderIds as a JSON array of longs")
        void bodyShapeMatchesContract() throws Exception {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(request -> {
                        String body = request.getBody().toString();
                        JsonNode parsed = MAPPER.readTree(body);
                        assertThat(parsed.get("userId").asLong()).isEqualTo(99L);
                        assertThat(parsed.get("orderIds").isArray()).isTrue();
                        assertThat(parsed.get("orderIds").size()).isEqualTo(2);
                        assertThat(parsed.get("orderIds").get(0).asLong()).isEqualTo(7L);
                        assertThat(parsed.get("orderIds").get(1).asLong()).isEqualTo(8L);
                    })
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            adapter.claimOrders(99L, List.of(7L, 8L));

            server.verify();
        }

        @Test
        @DisplayName("TC2: empty orderIds list is still sent as an empty JSON array")
        void emptyOrderIds_isStillSent() throws Exception {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("X-Internal-Api-Key", API_KEY);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(request -> {
                        String body = request.getBody().toString();
                        JsonNode parsed = MAPPER.readTree(body);
                        assertThat(parsed.get("userId").asLong()).isEqualTo(99L);
                        assertThat(parsed.get("orderIds").isArray()).isTrue();
                        assertThat(parsed.get("orderIds").size()).isZero();
                    })
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            HttpInternalOrdersClientAdapter adapter = new HttpInternalOrdersClientAdapter(builder.build());

            adapter.claimOrders(99L, List.of());

            server.verify();
        }
    }
}
