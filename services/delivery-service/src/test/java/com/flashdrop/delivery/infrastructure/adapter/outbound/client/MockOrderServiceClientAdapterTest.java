package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MockOrderServiceClientAdapter.
 * Verifies the mock returns empty/true as expected for testing scenarios
 * where orders-service is unavailable.
 */
class MockOrderServiceClientAdapterTest {

    @Nested
    @DisplayName("getOrdersByIds(List<Long>)")
    class GetOrdersByIds {

        @Test
        @DisplayName("TC1: always returns empty list")
        void alwaysReturnsEmptyList() {
            MockOrderServiceClientAdapter adapter = new MockOrderServiceClientAdapter();
            assertThat(adapter.getOrdersByIds(List.of(1L, 2L))).isEmpty();
        }

        @Test
        @DisplayName("TC2: null input — returns empty list")
        void nullInput_returnsEmptyList() {
            MockOrderServiceClientAdapter adapter = new MockOrderServiceClientAdapter();
            assertThat(adapter.getOrdersByIds(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("areOrdersFromSameRestaurant(List<Long>)")
    class AreOrdersFromSameRestaurant {

        @Test
        @DisplayName("TC1: always returns true")
        void alwaysReturnsTrue() {
            MockOrderServiceClientAdapter adapter = new MockOrderServiceClientAdapter();
            assertThat(adapter.areOrdersFromSameRestaurant(List.of(1L, 2L))).isTrue();
        }

        @Test
        @DisplayName("TC2: null input — returns true")
        void nullInput_returnsTrue() {
            MockOrderServiceClientAdapter adapter = new MockOrderServiceClientAdapter();
            assertThat(adapter.areOrdersFromSameRestaurant(null)).isTrue();
        }

        @Test
        @DisplayName("TC3: empty input — returns true")
        void emptyInput_returnsTrue() {
            MockOrderServiceClientAdapter adapter = new MockOrderServiceClientAdapter();
            assertThat(adapter.areOrdersFromSameRestaurant(List.of())).isTrue();
        }
    }
}
