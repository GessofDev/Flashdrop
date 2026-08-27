package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock.MockCatalogServer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class CatalogHttpClientAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private CatalogHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .defaultHeader("Accept", "application/json")
                .build();
        adapter = new CatalogHttpClientAdapter(restClient);
    }

    @Test
    void shouldReturnProductsWhenFound() {
        MockCatalogServer.stubGetProductsByIdsOkSingle(wireMock, 101L, 7L, "Burger", "Delicious", "img.jpg", 12500, true);

        List<ProductInfo> products = adapter.findProductsByIds(List.of(toUuid(101L)));

        assertEquals(1, products.size());
        assertEquals("Burger", products.get(0).getName());
        assertEquals(12500, products.get(0).getPrice().intValue());
        assertTrue(products.get(0).isAvailable());
        assertEquals(toUuid(7L), products.get(0).getRestaurantId());
    }

    @Test
    void shouldReturnEmptyListWhenNoProductsFound() {
        MockCatalogServer.stubGetProductsByIdsEmpty(wireMock);

        List<ProductInfo> products = adapter.findProductsByIds(List.of(toUuid(999L)));

        assertTrue(products.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForNullOrEmptyInput() {
        assertTrue(adapter.findProductsByIds(null).isEmpty());
        assertTrue(adapter.findProductsByIds(List.of()).isEmpty());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnServerError() {
        MockCatalogServer.stubGetProductsByIdsServerError(wireMock);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.findProductsByIds(List.of(toUuid(101L))));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Catalog error"));
    }

    @Test
    void shouldReturnRestaurantWhenFound() {
        MockCatalogServer.stubGetRestaurantByIdOk(wireMock, 7L, "Burgers House", "Los Leones 300", 42L);

        Optional<RestaurantInfo> result = adapter.findRestaurantById(toUuid(7L));

        assertTrue(result.isPresent());
        assertEquals("Burgers House", result.get().getName());
        assertEquals("Los Leones 300", result.get().getAddress());
        assertEquals(toUuid(7L), result.get().getRestaurantId());
    }

    @Test
    void shouldReturnEmptyWhenRestaurantNotFound() {
        MockCatalogServer.stubGetRestaurantByIdNotFound(wireMock, 999L);

        Optional<RestaurantInfo> result = adapter.findRestaurantById(toUuid(999L));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullRestaurantId() {
        Optional<RestaurantInfo> result = adapter.findRestaurantById(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnRestaurantServerError() {
        MockCatalogServer.stubGetRestaurantByIdServerError(wireMock, 7L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.findRestaurantById(toUuid(7L)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void shouldReturnRestaurantIdWhenFoundByUserId() {
        MockCatalogServer.stubGetRestaurantsByUserIdOk(wireMock, 42L, 7L, "Burgers House", "Los Leones 300");

        Optional<UUID> result = adapter.findRestaurantIdByUserId(toUuid(42L));

        assertTrue(result.isPresent());
        assertEquals(toUuid(7L), result.get());
    }

    @Test
    void shouldReturnEmptyWhenNoRestaurantForUser() {
        MockCatalogServer.stubGetRestaurantsByUserIdEmpty(wireMock, 999L);

        Optional<UUID> result = adapter.findRestaurantIdByUserId(toUuid(999L));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRestaurantByUserIdNotFound() {
        MockCatalogServer.stubGetRestaurantsByUserIdNotFound(wireMock, 999L);

        Optional<UUID> result = adapter.findRestaurantIdByUserId(toUuid(999L));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullUserId() {
        Optional<UUID> result = adapter.findRestaurantIdByUserId(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnRestaurantByUserIdServerError() {
        MockCatalogServer.stubGetRestaurantsByUserIdServerError(wireMock, 42L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.findRestaurantIdByUserId(toUuid(42L)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void shouldThrowExternalServiceExceptionBadGatewayWhenServiceDownOrTimeout() {
        RestClient offlineClient = RestClient.builder()
                .baseUrl("http://localhost:59999")
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .build();
        CatalogHttpClientAdapter offlineAdapter = new CatalogHttpClientAdapter(offlineClient);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> offlineAdapter.findProductsByIds(List.of(toUuid(101L))));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(ex.getMessage().contains("Catalog no disponible"));
    }

    private static UUID toUuid(long id) {
        return new UUID(0L, id);
    }
}

