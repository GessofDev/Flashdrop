package com.flashdrop.delivery.domain.model;

import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeliveryRoute")
class DeliveryRouteTest {

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("TC1: initial route has PENDIENTE status")
        void initialRouteHasPendienteStatus() {
            DeliveryRoute route = new DeliveryRoute(
                    null, 1L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                    RouteStatus.PENDIENTE, Instant.now()
            );
            assertThat(route.getStatus()).isEqualTo(RouteStatus.PENDIENTE);
        }

        @Test
        @DisplayName("TC2: route can transition from PENDIENTE to ASSIGNED")
        void routeCanTransitionToAssigned() {
            DeliveryRoute route = new DeliveryRoute(
                    1L, 1L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                    RouteStatus.PENDIENTE, Instant.now()
            );
            route.setStatus(RouteStatus.ASSIGNED);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.ASSIGNED);
        }

        @Test
        @DisplayName("TC3: route can transition from ASSIGNED to RETIRAR_PEDIDO")
        void routeCanTransitionToRetirarPedido() {
            DeliveryRoute route = new DeliveryRoute(
                    1L, 1L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                    RouteStatus.ASSIGNED, Instant.now()
            );
            route.setStatus(RouteStatus.RETIRAR_PEDIDO);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.RETIRAR_PEDIDO);
        }

        @Test
        @DisplayName("TC4: route can transition from RETIRAR_PEDIDO to EN_CAMINO")
        void routeCanTransitionToEnCamino() {
            DeliveryRoute route = new DeliveryRoute(
                    1L, 1L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                    RouteStatus.RETIRAR_PEDIDO, Instant.now()
            );
            route.setStatus(RouteStatus.EN_CAMINO);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.EN_CAMINO);
        }

        @Test
        @DisplayName("TC5: route can transition from EN_CAMINO to ENTREGADO")
        void routeCanTransitionToEntregado() {
            DeliveryRoute route = new DeliveryRoute(
                    1L, 1L, "Pickup A", "Delivery B",
                    Distance.of(BigDecimal.valueOf(5.0)), EstimatedTime.of(30),
                    RouteStatus.EN_CAMINO, Instant.now()
            );
            route.setStatus(RouteStatus.ENTREGADO);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.ENTREGADO);
        }
    }

    @Nested
    @DisplayName("field mapping")
    class FieldMapping {

        @Test
        @DisplayName("TC6: all fields are correctly stored and retrieved")
        void allFieldsStoredAndRetrieved() {
            Instant createdAt = Instant.parse("2024-07-29T10:00:00Z");
            DeliveryRoute route = new DeliveryRoute(
                    42L, 101L, "Calle Pickup 123", "Avenida Delivery 456",
                    Distance.of(BigDecimal.valueOf(7.5)), EstimatedTime.of(45),
                    RouteStatus.EN_CAMINO, createdAt
            );

            assertThat(route.getId()).isEqualTo(42L);
            assertThat(route.getOrderId()).isEqualTo(101L);
            assertThat(route.getPickupAddress()).isEqualTo("Calle Pickup 123");
            assertThat(route.getDeliveryAddress()).isEqualTo("Avenida Delivery 456");
            assertThat(route.getDistanceKm().value()).isEqualByComparingTo(BigDecimal.valueOf(7.5));
            assertThat(route.getEstimatedMinutes().minutes()).isEqualTo(45);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.EN_CAMINO);
            assertThat(route.getCreatedAt()).isEqualTo(createdAt);
        }
    }
}
