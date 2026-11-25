package com.flashdrop.reviews.infrastructure.adapter.outbound.client;

import com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpRestaurantLookupClientAdapterContractTest {

    private static final String API_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpRestaurantLookupClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://catalog-service:8082")
                .defaultHeader("X-Internal-Api-Key", API_KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpRestaurantLookupClientAdapter(builder.build());
    }

    @Test
    void checkRestaurantMembership_returnsTrueOn204() {
        server.expect(requestTo("http://catalog-service:8082/api/internal/owner/restaurants/7/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", API_KEY))
                .andRespond(withNoContent());

        assertThat(adapter.checkRestaurantMembership(42L, 7L)).isTrue();
        server.verify();
    }

    @Test
    void checkRestaurantMembership_returnsFalseOn404() {
        server.expect(requestTo("http://catalog-service:8082/api/internal/owner/restaurants/99/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", API_KEY))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.checkRestaurantMembership(42L, 99L)).isFalse();
        server.verify();
    }

    @Test
    void checkRestaurantMembership_failsClosedOnServerError() {
        server.expect(requestTo("http://catalog-service:8082/api/internal/owner/restaurants/7/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", API_KEY))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.checkRestaurantMembership(42L, 7L))
                .isInstanceOf(InternalServiceUnavailableException.class);
        server.verify();
    }
}
