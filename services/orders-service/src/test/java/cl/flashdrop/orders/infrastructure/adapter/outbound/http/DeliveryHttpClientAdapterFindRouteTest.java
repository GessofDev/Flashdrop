package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeliveryHttpClientAdapterFindRouteTest {

    @Test
    void findRouteByOrderId_existingRoute_returnsRouteWithNumericId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://delivery.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeliveryHttpClientAdapter adapter = new DeliveryHttpClientAdapter(builder.build());

        UUID orderId = IdConverter.toUuid(42L);
        server.expect(requestTo("http://delivery.test/api/internal/routes/order/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "OK",
                          "data": {
                            "id": 77,
                            "orderId": 42,
                            "pickupAddress": "Av. Providencia 123",
                            "deliveryAddress": "Calle Falsa 456",
                            "distanceKm": 2.4,
                            "estimatedMinutes": 14,
                            "status": "En camino"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<DeliveryRoute> result = adapter.findRouteByOrderId(orderId);

        server.verify();
        assertThat(result).isPresent();
        assertThat(result.get().getRouteId()).isEqualTo(77L);
        assertThat(result.get().getStatus()).isEqualTo("En camino");
    }

    @Test
    void findRouteByOrderId_notFound_returnsEmpty() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://delivery.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeliveryHttpClientAdapter adapter = new DeliveryHttpClientAdapter(builder.build());

        UUID orderId = IdConverter.toUuid(99L);
        server.expect(requestTo("http://delivery.test/api/internal/routes/order/99"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<DeliveryRoute> result = adapter.findRouteByOrderId(orderId);

        server.verify();
        assertThat(result).isEmpty();
    }
}
