package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogHttpClientAdapterContractTest {

    private MockRestServiceServer server;
    private CatalogHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.internal");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new CatalogHttpClientAdapter(builder.build());
    }

    @Test
    void findRestaurantById_parsesMerchantId() {
        server.expect(requestTo("http://catalog.internal/api/internal/restaurants/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 7,
                          "userId": 42,
                          "merchantId": 88,
                          "name": "Local Centro",
                          "address": "Alameda 100",
                          "open": true
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<RestaurantInfo> result = adapter.findRestaurantById(IdConverter.toUuid(7L));

        server.verify();
        assertTrue(result.isPresent());
        assertEquals(88L, result.get().getMerchantId());
        assertEquals("Local Centro", result.get().getName());
    }

    @Test
    void findOperableRestaurantIdsByUserId_returnsAllOperableIds() {
        server.expect(requestTo("http://catalog.internal/api/internal/owner/restaurants?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"id": 7, "userId": 42, "merchantId": 88, "name": "Sucursal 1", "address": "Calle 1", "open": true},
                          {"id": 9, "userId": 42, "merchantId": 88, "name": "Sucursal 2", "address": "Calle 2", "open": true}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<UUID> ids = adapter.findOperableRestaurantIdsByUserId(IdConverter.toUuid(42L));

        server.verify();
        assertEquals(List.of(IdConverter.toUuid(7L), IdConverter.toUuid(9L)), ids);
    }

    @Test
    void checkRestaurantMembership_returnsTrueOn204_andFalseOn404() {
        server.expect(requestTo("http://catalog.internal/api/internal/owner/restaurants/7/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withNoContent());

        assertTrue(adapter.checkRestaurantMembership(IdConverter.toUuid(42L), IdConverter.toUuid(7L)));
        server.verify();

        server.reset();
        server.expect(requestTo("http://catalog.internal/api/internal/owner/restaurants/99/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(adapter.checkRestaurantMembership(IdConverter.toUuid(42L), IdConverter.toUuid(99L)));
        server.verify();
    }

    @Test
    void checkRestaurantMembership_failsClosedOnServerError() {
        server.expect(requestTo("http://catalog.internal/api/internal/owner/restaurants/7/membership?userId=42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> adapter.checkRestaurantMembership(IdConverter.toUuid(42L), IdConverter.toUuid(7L)));

        server.verify();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }
}
