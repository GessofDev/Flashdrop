package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaRouteRepositoryAdapterTest {

    @Mock
    private JpaDeliveryRouteRepository jpaRepository;

    @Nested
    @DisplayName("RouteRepository interface implementation")
    class RouteRepositoryInterface {

        @Test
        @DisplayName("TC1: JpaRouteRepositoryAdapter implements RouteRepository")
        void implementsRouteRepository() {
            RouteRepository adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            assertThat(adapter).isNotNull();
        }
    }

    @Nested
    @DisplayName("findById mapping")
    class FindByIdMapping {

        @Test
        @DisplayName("TC2: maps entity to DeliveryRoute domain model with all fields")
        void mapsAllFieldsCorrectly() {
            DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                    1L, 101L, "Pickup St", "Delivery St",
                    new java.math.BigDecimal("5.5"), 30,
                    "Pendiente", Instant.parse("2024-07-29T10:00:00Z")
            );
            when(jpaRepository.findById(1L)).thenReturn(Optional.of(entity));

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            Optional<DeliveryRoute> result = adapter.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            assertThat(result.get().getOrderId()).isEqualTo(101L);
            assertThat(result.get().getPickupAddress()).isEqualTo("Pickup St");
            assertThat(result.get().getDeliveryAddress()).isEqualTo("Delivery St");
            assertThat(result.get().getDistanceKm().value()).isEqualByComparingTo(new java.math.BigDecimal("5.5"));
            assertThat(result.get().getEstimatedMinutes().minutes()).isEqualTo(30);
            assertThat(result.get().getStatus()).isEqualTo(RouteStatus.PENDIENTE);
            assertThat(result.get().getCreatedAt()).isEqualTo(Instant.parse("2024-07-29T10:00:00Z"));
        }

        @Test
        @DisplayName("TC3: returns empty when entity not found")
        void returnsEmptyWhenNotFound() {
            when(jpaRepository.findById(999L)).thenReturn(Optional.empty());

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            Optional<DeliveryRoute> result = adapter.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByOrderId mapping")
    class FindByOrderIdMapping {

        @Test
        @DisplayName("TC4: maps entity to DeliveryRoute from orderId query")
        void mapsEntityFromOrderId() {
            DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                    5L, 202L, "Pickup", "Delivery",
                    new java.math.BigDecimal("2.0"), 15,
                    "Listo para retiro", Instant.now()
            );
            when(jpaRepository.findByOrderId(202L)).thenReturn(Optional.of(entity));

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            Optional<DeliveryRoute> result = adapter.findByOrderId(202L);

            assertThat(result).isPresent();
            assertThat(result.get().getOrderId()).isEqualTo(202L);
        }
    }

    @Nested
    @DisplayName("findByDeliveryPersonId mapping")
    class FindByDeliveryPersonIdMapping {

        @Test
        @DisplayName("TC10: delegates to Spring Data and maps deliveryPersonId back to domain")
        void delegatesAndMapsDeliveryPersonId() {
            DeliveryRouteJpaEntity e1 = new DeliveryRouteJpaEntity(
                    1L, 101L, 7L, "Pickup1", "Delivery1",
                    new java.math.BigDecimal("3.0"), 15,
                    "Pendiente", Instant.now());
            DeliveryRouteJpaEntity e2 = new DeliveryRouteJpaEntity(
                    2L, 102L, 7L, "Pickup2", "Delivery2",
                    new java.math.BigDecimal("4.0"), 25,
                    "En camino", Instant.now());
            when(jpaRepository.findByDeliveryPersonId(7L)).thenReturn(List.of(e1, e2));

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            List<DeliveryRoute> result = adapter.findByDeliveryPersonId(7L);

            verify(jpaRepository, times(1)).findByDeliveryPersonId(7L);
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getDeliveryPersonId() != null && r.getDeliveryPersonId() == 7L);
            assertThat(result.get(0).getOrderId()).isEqualTo(101L);
            assertThat(result.get(1).getOrderId()).isEqualTo(102L);
        }

        @Test
        @DisplayName("TC11: returns empty list when no routes for the courier")
        void returnsEmptyWhenNoRoutesForCourier() {
            when(jpaRepository.findByDeliveryPersonId(99L)).thenReturn(List.of());

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            List<DeliveryRoute> result = adapter.findByDeliveryPersonId(99L);

            verify(jpaRepository, times(1)).findByDeliveryPersonId(99L);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll mapping")
    class FindAllMapping {

        @Test
        @DisplayName("TC5: maps multiple entities to DeliveryRoute list")
        void mapsMultipleEntities() {
            List<DeliveryRouteJpaEntity> entities = List.of(
                    new DeliveryRouteJpaEntity(1L, 101L, "A", "B", new java.math.BigDecimal("1"), 10, "Pendiente", Instant.now()),
                    new DeliveryRouteJpaEntity(2L, 102L, "C", "D", new java.math.BigDecimal("2"), 20, "En camino", Instant.now())
            );
            when(jpaRepository.findAll()).thenReturn(entities);

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            List<DeliveryRoute> result = adapter.findAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderId()).isEqualTo(101L);
            assertThat(result.get(1).getOrderId()).isEqualTo(102L);
        }
    }

    @Nested
    @DisplayName("save mapping")
    class SaveMapping {

        @Test
        @DisplayName("TC6: maps domain model to entity and saves")
        void mapsAndSavesCorrectly() {
            DeliveryRoute route = new DeliveryRoute(
                    null, 301L, "Pickup", "Delivery",
                    Distance.of(java.math.BigDecimal.valueOf(3.5)), EstimatedTime.of(20),
                    RouteStatus.PENDIENTE, Instant.now()
            );
            DeliveryRouteJpaEntity savedEntity = new DeliveryRouteJpaEntity(
                    10L, 301L, "Pickup", "Delivery",
                    new java.math.BigDecimal("3.5"), 20,
                    "Pendiente", Instant.now()
            );
            when(jpaRepository.save(any(DeliveryRouteJpaEntity.class))).thenReturn(savedEntity);

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            DeliveryRoute result = adapter.save(route);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getOrderId()).isEqualTo(301L);
            verify(jpaRepository, times(1)).save(any(DeliveryRouteJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("existsByOrderId")
    class ExistsByOrderId {

        @Test
        @DisplayName("TC7: returns true when orderId exists")
        void returnsTrueWhenExists() {
            when(jpaRepository.findByOrderId(401L)).thenReturn(Optional.of(
                    new DeliveryRouteJpaEntity(1L, 401L, "A", "B", null, null, "Pendiente", Instant.now())
            ));

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            assertThat(adapter.existsByOrderId(401L)).isTrue();
        }

        @Test
        @DisplayName("TC8: returns false when orderId does not exist")
        void returnsFalseWhenNotExists() {
            when(jpaRepository.findByOrderId(999L)).thenReturn(Optional.empty());

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            assertThat(adapter.existsByOrderId(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("updateStatus mapping")
    class UpdateStatusMapping {

        @Test
        @DisplayName("TC9: updates status and returns updated DeliveryRoute")
        void updatesStatusCorrectly() {
            DeliveryRouteJpaEntity existing = new DeliveryRouteJpaEntity(
                    7L, 501L, "Pickup", "Delivery",
                    new java.math.BigDecimal("1.0"), 5,
                    "Pendiente", Instant.now()
            );
            DeliveryRouteJpaEntity updated = new DeliveryRouteJpaEntity(
                    7L, 501L, "Pickup", "Delivery",
                    new java.math.BigDecimal("1.0"), 5,
                    "Entregado", Instant.now()
            );
            when(jpaRepository.findById(7L)).thenReturn(Optional.of(existing));
            when(jpaRepository.save(any(DeliveryRouteJpaEntity.class))).thenReturn(updated);

            JpaRouteRepositoryAdapter adapter = new JpaRouteRepositoryAdapter(jpaRepository);
            DeliveryRoute result = adapter.updateStatus(7L, "Entregado");

            assertThat(result.getStatus()).isEqualTo(RouteStatus.ENTREGADO);
        }
    }
}
