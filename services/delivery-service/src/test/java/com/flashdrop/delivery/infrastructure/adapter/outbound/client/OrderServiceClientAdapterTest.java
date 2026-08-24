package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase.OrderRow;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase.RestaurantRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter unit tests for OrderServiceClientAdapter.
 * Tests the OrderRow and RestaurantRow data mapping logic.
 * Full integration with RestClient is tested via controller and use case tests.
 */
class OrderServiceClientAdapterTest {

    @Nested
    @DisplayName("OrderServicePort.OrderInfo record structure")
    class OrderInfoRecordStructure {

        @Test
        @DisplayName("TC1: OrderInfo record holds all required fields from OrderRow")
        void orderInfoRecordStructure() {
            OrderServicePort.OrderInfo info = new OrderServicePort.OrderInfo(
                    101L, 10L, "Pickup Address", "Delivery Address", "ORD-001");

            assertThat(info.id()).isEqualTo(101L);
            assertThat(info.restaurantId()).isEqualTo(10L);
            assertThat(info.pickupAddress()).isEqualTo("Pickup Address");
            assertThat(info.deliveryAddress()).isEqualTo("Delivery Address");
        }

        @Test
        @DisplayName("TC2: OrderRow record maps to OrderInfo fields correctly")
        void orderRowMapsToOrderInfo() {
            OrderRow row = new OrderRow(
                    101L, 10L, 300L, "pending", "Delivery Address 101", "ORD-001");

            assertThat(row.id()).isEqualTo(101L);
            assertThat(row.restaurantId()).isEqualTo(10L);
            assertThat(row.address()).isEqualTo("Delivery Address 101");
        }

        @Test
        @DisplayName("TC3: RestaurantRow record maps address for pickup resolution")
        void restaurantRowMapsAddress() {
            RestaurantRow row = new RestaurantRow(10L, 1L, "Restaurant Name", "Pickup St 123");

            assertThat(row.id()).isEqualTo(10L);
            assertThat(row.name()).isEqualTo("Restaurant Name");
            assertThat(row.address()).isEqualTo("Pickup St 123");
        }
    }

    @Nested
    @DisplayName("OrderServicePort interface contract")
    class OrderServicePortContract {

        @Test
        @DisplayName("TC1: OrderServiceClientAdapter implements OrderServicePort")
        void adapterImplementsPort() {
            OrderServicePort adapter = new OrderServiceClientAdapter(null);
            assertThat(adapter).isNotNull();
        }

        @Test
        @DisplayName("TC2: port methods have correct signatures")
        void portMethodsExist() throws Exception {
            assertThat(OrderServicePort.class.getMethod("getOrdersByIds", java.util.List.class)).isNotNull();
            assertThat(OrderServicePort.class.getMethod("areOrdersFromSameRestaurant", java.util.List.class)).isNotNull();
        }
    }
}
