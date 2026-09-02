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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
        MockDeliveryServer.stubGetDeliveryByUserIdOk(wireMock, 42L, 9L, "MOTO");

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

    // ── C-7: PATCH /api/internal/routes/order/{orderId}/status ─────────
    //
    // GAP-01 (documentado en TEAM_INTERNAL_API_CONTRACTS.md §9) fue resuelto
    // en delivery-service el 2026-09-01 (commit 904464d) y el wire-up del
    // lado de Orders (GAP-01b) implementado el mismo día. Verificado en vivo
    // contra Postgres real antes de este cambio; estos tests fijan ese
    // comportamiento con WireMock.
    //
    // Diseño deliberado: updateRouteStatusByOrder/updateRouteStatus NUNCA
    // lanzan excepción por una falla de Delivery (404/400/5xx/timeout) — se
    // degradan con gracia (log WARN + continúan) para no tumbar la
    // transacción de claim ni la de cambio de estado por un problema de
    // sincronización secundaria. Esto es intencional y distinto de saveRoute
    // (creación de ruta), que sigue propagando el error.

    @Test
    void shouldUpdateRouteStatusByOrderSuccessfully() {
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 501L);

        assertDoesNotThrow(() -> adapter.updateRouteStatusByOrder(toUuid(501L), "En camino"));

        // "En camino" (texto de OrderStatus en Orders) debe traducirse al token
        // del enum RouteStatus que Delivery valida ("EN_CAMINO"), no reenviarse tal cual.
        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/501/status"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("EN_CAMINO"))));
    }

    @Test
    void shouldDegradeGracefullyWhenNoRouteFoundOnUpdate() {
        MockDeliveryServer.stubUpdateRouteByOrderNotFound(wireMock, 999L);

        assertDoesNotThrow(() -> adapter.updateRouteStatusByOrder(toUuid(999L), "En camino"));
    }

    @Test
    void shouldDegradeGracefullyOnUpdateRouteStatusServerError() {
        MockDeliveryServer.stubUpdateRouteByOrderServerError(wireMock, 501L);

        assertDoesNotThrow(() -> adapter.updateRouteStatusByOrder(toUuid(501L), "En camino"));
    }

    @Test
    void shouldSkipSyncByOrderWhenOrderStatusHasNoRouteEquivalent() {
        // "Nuevo pedido" y "Preparando" no tienen estado de ruta equivalente en Delivery
        // (la ruta recién existe desde "Listo para retiro" en adelante) — no se llama a Delivery.
        adapter.updateRouteStatusByOrder(toUuid(501L), "Nuevo pedido");

        wireMock.verify(0, patchRequestedFor(urlPathMatching("/api/internal/routes/.*")));
    }

    @Test
    void shouldUpdateRouteStatusBulkSuccessfully() {
        // No existe endpoint bulk en delivery-service todavía (ver GAP-01b):
        // updateRouteStatus llama al endpoint single una vez por orderId.
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 501L);
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 502L);
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 503L);

        List<UUID> orderIds = List.of(toUuid(501L), toUuid(502L), toUuid(503L));

        assertDoesNotThrow(() -> adapter.updateRouteStatus(orderIds, "En camino"));

        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/501/status")));
        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/502/status")));
        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/503/status")));
    }

    @Test
    void shouldDoNothingForNullOrEmptyOrderIds() {
        assertDoesNotThrow(() -> adapter.updateRouteStatus(null, "En camino"));
        assertDoesNotThrow(() -> adapter.updateRouteStatus(List.of(), "En camino"));
    }

    @Test
    void shouldSkipBulkSyncWhenOrderStatusHasNoRouteEquivalent() {
        adapter.updateRouteStatus(List.of(toUuid(501L), toUuid(502L)), "Preparando");

        wireMock.verify(0, patchRequestedFor(urlPathMatching("/api/internal/routes/.*")));
    }

    @Test
    void shouldDegradeGracefullyOnBulkUpdateServerErrorAndContinueWithOthers() {
        // 501 falla, 502 responde OK: una falla no debe cortar el resto del batch.
        MockDeliveryServer.stubUpdateRouteByOrderServerError(wireMock, 501L);
        MockDeliveryServer.stubUpdateRouteByOrderOk(wireMock, 502L);

        List<UUID> orderIds = List.of(toUuid(501L), toUuid(502L));

        assertDoesNotThrow(() -> adapter.updateRouteStatus(orderIds, "En camino"));

        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/501/status")));
        wireMock.verify(patchRequestedFor(urlEqualTo("/api/internal/routes/order/502/status")));
    }

    private static UUID toUuid(long id) {
        return new UUID(0L, id);
    }
}
