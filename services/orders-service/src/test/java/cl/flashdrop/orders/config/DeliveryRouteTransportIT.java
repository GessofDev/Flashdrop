package cl.flashdrop.orders.config;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.DeliveryHttpClientAdapter;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryRouteTransportIT {

    @RegisterExtension
    static WireMockExtension delivery = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private DeliveryHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        // Exercise the production factory, not a replacement test-only transport.
        adapter = new DeliveryHttpClientAdapter(new InternalServiceClientConfig()
                .deliveryInternalRestClient(delivery.baseUrl(), "transport-test-key"));
    }

    @ParameterizedTest
    @CsvSource({"Listo para retiro,RETIRAR_PEDIDO", "Retirado,EN_CAMINO", "Entregado,ENTREGADO"})
    void synchronizeStatus_validTransition_sendsAuthenticatedPatch(String orderStatus, String routeStatus) {
        String path = "/api/internal/routes/order/25/status";
        delivery.stubFor(patch(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));

        adapter.updateRouteStatusByOrder(new UUID(0, 25), orderStatus);

        delivery.verify(1, patchRequestedFor(urlEqualTo(path))
                .withHeader("X-Internal-Api-Key", equalTo("transport-test-key"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("{\"status\":\"" + routeStatus + "\"}")));
    }

    @Test
    void lookupAndCreate_validRequests_preservesGetAndPostContracts() {
        delivery.stubFor(get(urlEqualTo("/api/internal/delivery-persons?userId=4"))
                .willReturn(okJson("{\"success\":true,\"data\":{\"id\":3,\"userId\":\"4\",\"vehicle\":null}}")));
        delivery.stubFor(post(urlEqualTo("/api/internal/routes"))
                .willReturn(aResponse().withStatus(201)));

        assertEquals(new UUID(0, 3), adapter.findDeliveryIdByUserId(new UUID(0, 4)).orElseThrow());
        adapter.saveRoute(DeliveryRoute.builder().orderId(new UUID(0, 25))
                .pickupAddress("QA pickup").deliveryAddress("QA dropoff")
                .distanceKm(BigDecimal.ONE).estimatedMinutes(10).status("Pendiente").build());

        delivery.verify(getRequestedFor(urlEqualTo("/api/internal/delivery-persons?userId=4"))
                .withHeader("X-Internal-Api-Key", equalTo("transport-test-key")));
        delivery.verify(postRequestedFor(urlEqualTo("/api/internal/routes"))
                .withHeader("X-Internal-Api-Key", equalTo("transport-test-key"))
                .withRequestBody(matchingJsonPath("$.orderId", equalTo("25"))));
    }
}
