package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HttpOrderServiceClientAdapter.
 *
 * Key behaviors tested here (unit level):
 * - Null/empty input handling (no HTTP call made)
 * - areOrdersFromSameRestaurant null/empty handling
 *
 * Full HTTP behavior (5xx graceful degradation, header verification)
 * is covered by integration tests that use a real RestClient with
 * a mock server or by the use-case-level tests that mock this adapter.
 */
class HttpOrderServiceClientAdapterTest {

    @Nested
    @DisplayName("getOrdersByIds(List<Long>) — input validation")
    class GetOrdersByIdsInputValidation {

        @Test
        @DisplayName("TC1: empty orderIds — returns empty list without HTTP call")
        void emptyList_returnsEmptyWithoutHttpCall() {
            // Use a spy with a mock RestClient that would fail if called
            var mockRestClient = Proxy.newProxyInstance(
                    RestClient.class.getClassLoader(),
                    new Class<?>[]{RestClient.class},
                    (proxy, method, args) -> {
                        throw new RuntimeException("HTTP call should not be made for empty list");
                    });

            HttpOrderServiceClientAdapter adapter =
                    new HttpOrderServiceClientAdapter((RestClient) mockRestClient, "http://orders:8083");

            List<OrderServicePort.OrderInfo> result = adapter.getOrdersByIds(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC2: null orderIds — returns empty list")
        void nullList_returnsEmptyList() {
            var mockRestClient = Proxy.newProxyInstance(
                    RestClient.class.getClassLoader(),
                    new Class<?>[]{RestClient.class},
                    (proxy, method, args) -> {
                        throw new RuntimeException("HTTP call should not be made for null list");
                    });

            HttpOrderServiceClientAdapter adapter =
                    new HttpOrderServiceClientAdapter((RestClient) mockRestClient, "http://orders:8083");

            List<OrderServicePort.OrderInfo> result = adapter.getOrdersByIds(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("areOrdersFromSameRestaurant(List<Long>) — input validation")
    class AreOrdersFromSameRestaurantInputValidation {

        @Test
        @DisplayName("TC1: empty list — returns true without HTTP call")
        void emptyList_returnsTrue() {
            var mockRestClient = Proxy.newProxyInstance(
                    RestClient.class.getClassLoader(),
                    new Class<?>[]{RestClient.class},
                    (proxy, method, args) -> {
                        throw new RuntimeException("HTTP call should not be made for empty list");
                    });

            HttpOrderServiceClientAdapter adapter =
                    new HttpOrderServiceClientAdapter((RestClient) mockRestClient, "http://orders:8083");

            boolean result = adapter.areOrdersFromSameRestaurant(List.of());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC2: null list — returns true")
        void nullList_returnsTrue() {
            var mockRestClient = Proxy.newProxyInstance(
                    RestClient.class.getClassLoader(),
                    new Class<?>[]{RestClient.class},
                    (proxy, method, args) -> {
                        throw new RuntimeException("HTTP call should not be made for null list");
                    });

            HttpOrderServiceClientAdapter adapter =
                    new HttpOrderServiceClientAdapter((RestClient) mockRestClient, "http://orders:8083");

            boolean result = adapter.areOrdersFromSameRestaurant(null);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("OrderInfo record")
    class OrderInfoRecord {

        @Test
        @DisplayName("TC1: OrderInfo record holds all required fields")
        void orderInfoRecordFields() {
            OrderServicePort.OrderInfo info = new OrderServicePort.OrderInfo(
                    101L, 10L, "Pickup Address", "Delivery Address", "ORD-001");

            assertThat(info.id()).isEqualTo(101L);
            assertThat(info.restaurantId()).isEqualTo(10L);
            assertThat(info.pickupAddress()).isEqualTo("Pickup Address");
            assertThat(info.deliveryAddress()).isEqualTo("Delivery Address");
            assertThat(info.code()).isEqualTo("ORD-001");
        }

        @Test
        @DisplayName("TC2: HttpOrderServiceClientAdapter implements OrderServicePort")
        void adapterImplementsPort() {
            var mockRestClient = Proxy.newProxyInstance(
                    RestClient.class.getClassLoader(),
                    new Class<?>[]{RestClient.class},
                    (proxy, method, args) -> null);

            OrderServicePort adapter = new HttpOrderServiceClientAdapter(
                    (RestClient) mockRestClient, "http://orders:8083");

            assertThat(adapter).isNotNull();
            assertThat(adapter).isInstanceOf(OrderServicePort.class);
        }
    }
}
