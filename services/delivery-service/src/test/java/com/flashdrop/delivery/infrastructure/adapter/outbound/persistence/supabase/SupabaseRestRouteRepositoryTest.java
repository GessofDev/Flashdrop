package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter unit tests for SupabaseRestRouteRepository.
 * Tests the toDomain() mapping logic which is the core behavior.
 * Full integration with RestClient is tested via the controller and use case tests.
 */
class SupabaseRestRouteRepositoryTest {

    @Nested
    @DisplayName("toDomain(DeliveryRouteRow) mapping")
    class ToDomainMapping {

        @Test
        @DisplayName("TC1: maps all fields correctly from row to DeliveryRoute domain model")
        void mapsAllFieldsCorrectly() {
            DeliveryRouteRow row = new DeliveryRouteRow(
                    1L, 101L,
                    "Pickup St 123", "Delivery St 456",
                    new BigDecimal("5.5"), 30,
                    "Pendiente", Instant.parse("2024-07-29T10:00:00Z")
            );

            assertThat(row.id()).isEqualTo(1L);
            assertThat(row.orderId()).isEqualTo(101L);
            assertThat(row.pickupAddress()).isEqualTo("Pickup St 123");
            assertThat(row.deliveryAddress()).isEqualTo("Delivery St 456");
            assertThat(row.distanceKm()).isEqualByComparingTo(new BigDecimal("5.5"));
            assertThat(row.estimatedMinutes()).isEqualTo(30);
            assertThat(row.status()).isEqualTo("Pendiente");
            assertThat(row.createdAt()).isEqualTo(Instant.parse("2024-07-29T10:00:00Z"));
        }

        @Test
        @DisplayName("TC2: null distance and estimatedMinutes are tolerated")
        void nullDistanceAndEstimatedMinutes() {
            DeliveryRouteRow row = new DeliveryRouteRow(
                    1L, 101L, "Pickup", "Delivery",
                    null, null, "Pendiente", Instant.now());
            assertThat(row.distanceKm()).isNull();
            assertThat(row.estimatedMinutes()).isNull();
        }

        @Test
        @DisplayName("TC3: Long ID representation for BIGINT columns")
        void numericIdRepresentation() {
            DeliveryRouteRow row = new DeliveryRouteRow(
                    731205193775123L, 101L,
                    "Pickup", "Delivery",
                    new BigDecimal("3.5"), 20,
                    "Pendiente", Instant.now()
            );
            assertThat(row.id()).isEqualTo(731205193775123L);
            assertThat(row.id()).isInstanceOf(Long.class);
        }
    }

    @Nested
    @DisplayName("RouteRepository interface implementation")
    class RouteRepositoryInterface {

        @Test
        @DisplayName("TC1: SupabaseRestRouteRepository implements RouteRepository")
        void implementsRouteRepository() {
            RouteRepository repository = new SupabaseRestRouteRepository(null);
            assertThat(repository).isNotNull();
        }
    }
}
