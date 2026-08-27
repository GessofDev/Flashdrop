package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock.MockDeliveryServer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryHttpClientAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private DeliveryHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        RestClient restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        adapter = new DeliveryHttpClientAdapter(restClient);
    }

    @Test
    void shouldReturnDeliveryIdWhenFound() {
        MockDeliveryServer.stubGetDeliveryByUserIdOk(wireMock, 42L, 9L, "Carlos B.", "+56999999999");

        Optional<UUID> result = adapter.findDeliveryIdByUserId(toUuid(42L));

        assertTrue(result.isPresent());
        assertEquals(toUuid(9L), result.get());
    }

    @Test
    void shouldReturnEmptyWhenDeliveryNotFound() {
        MockDeliveryServer.stubGetDeliveryByUserIdNotFound(wireMock, 999L);

        Optional<UUID> result = adapter.findDeliveryIdByUserId(toUuid(999L));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullUserId() {
        Optional<UUID> result = adapter.findDeliveryIdByUserId(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnDeliveryServerError() {
        MockDeliveryServer.stubGetDeliveryByUserIdServerError(wireMock, 42L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.findDeliveryIdByUserId(toUuid(42L)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Delivery error"));
    }

    @Test
    void shouldCreateRouteSuccessfully() {
        MockDeliveryServer.stubCreateRouteOk(wireMock);

        DeliveryRoute route = DeliveryRoute.builder()
                .orderId(toUuid(501L))
                .pickupAddress("Burgers House, Los Leones 300")
                .deliveryAddress("Av. Providencia 1200")
                .distanceKm(BigDecimal.valueOf(3.2))
                .estimatedMinutes(20)
                .status("Pendiente")
                .build();

        assertDoesNotThrow(() -> adapter.saveRoute(route));
    }

    @Test
    void shouldThrowExternalServiceExceptionOnCreateRouteError() {
        MockDeliveryServer.stubCreateRouteServerError(wireMock);

        DeliveryRoute route = DeliveryRoute.builder()
                .orderId(toUuid(501L))
                .pickupAddress("Burgers House")
                .deliveryAddress("Av. Providencia")
                .distanceKm(BigDecimal.valueOf(3.2))
                .estimatedMinutes(20)
                .status("Pendiente")
                .build();

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.saveRoute(route));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void shouldUpdateRouteStatusByOrderSuccessfully() {
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 501L);

        assertDoesNotThrow(() -> adapter.updateRouteStatusByOrder(toUuid(501L), "En camino"));
    }

    @Test
    void shouldThrowOnRouteNotFoundOnUpdate() {
        MockDeliveryServer.stubUpdateRouteByOrderNotFound(wireMock, 999L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.updateRouteStatusByOrder(toUuid(999L), "En camino"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnUpdateRouteStatusServerError() {
        MockDeliveryServer.stubUpdateRouteByOrderServerError(wireMock, 501L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.updateRouteStatusByOrder(toUuid(501L), "En camino"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void shouldUpdateRouteStatusBulkSuccessfully() {
        MockDeliveryServer.stubUpdateRoutesBulkOk(wireMock);

        List<UUID> orderIds = List.of(toUuid(501L), toUuid(502L), toUuid(503L));

        assertDoesNotThrow(() -> adapter.updateRouteStatus(orderIds, "En camino"));
    }

    @Test
    void shouldDoNothingForNullOrEmptyOrderIds() {
        assertDoesNotThrow(() -> adapter.updateRouteStatus(null, "En camino"));
        assertDoesNotThrow(() -> adapter.updateRouteStatus(List.of(), "En camino"));
    }

    @Test
    void shouldThrowExternalServiceExceptionOnBulkUpdateServerError() {
        MockDeliveryServer.stubUpdateRoutesBulkServerError(wireMock);

        List<UUID> orderIds = List.of(toUuid(501L), toUuid(502L));

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.updateRouteStatus(orderIds, "En camino"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    private static UUID toUuid(long id) {
        return new UUID(0L, id);
    }
}
