package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaDeliveryPersonRepositoryAdapterTest {

    @Mock
    private JpaDeliveryPersonRepository jpaRepository;

    @Nested
    @DisplayName("DeliveryPersonRepository interface implementation")
    class DeliveryPersonRepositoryInterface {

        @Test
        @DisplayName("TC1: JpaDeliveryPersonRepositoryAdapter implements DeliveryPersonRepository")
        void implementsDeliveryPersonRepository() {
            DeliveryPersonRepository adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            assertThat(adapter).isNotNull();
        }
    }

    @Nested
    @DisplayName("findById mapping")
    class FindByIdMapping {

        @Test
        @DisplayName("TC2: maps entity to DeliveryPerson domain model with all fields")
        void mapsAllFieldsCorrectly() {
            DeliveryPersonJpaEntity entity = new DeliveryPersonJpaEntity(
                    1L, "U1", true, Instant.parse("2024-07-29T10:00:00Z")
            );
            when(jpaRepository.findById(1L)).thenReturn(Optional.of(entity));

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            Optional<DeliveryPerson> result = adapter.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            assertThat(result.get().getUserId()).isEqualTo("U1");
            assertThat(result.get().getVehicle()).isNull();
            assertThat(result.get().getCreatedAt()).isEqualTo(Instant.parse("2024-07-29T10:00:00Z"));
        }

        @Test
        @DisplayName("TC3: returns empty when entity not found")
        void returnsEmptyWhenNotFound() {
            when(jpaRepository.findById(999L)).thenReturn(Optional.empty());

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            Optional<DeliveryPerson> result = adapter.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserId mapping")
    class FindByUserIdMapping {

        @Test
        @DisplayName("TC4: maps entity to DeliveryPerson from userId query")
        void mapsEntityFromUserId() {
            DeliveryPersonJpaEntity entity = new DeliveryPersonJpaEntity(
                    5L, "U2", false, Instant.now()
            );
            when(jpaRepository.findByUserId("U2")).thenReturn(Optional.of(entity));

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            Optional<DeliveryPerson> result = adapter.findByUserId("U2");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo("U2");
            assertThat(result.get().getVehicle()).isNull();
        }
    }

    @Nested
    @DisplayName("save mapping")
    class SaveMapping {

        @Test
        @DisplayName("TC5: maps domain model to entity and saves")
        void mapsAndSavesCorrectly() {
            DeliveryPerson person = new DeliveryPerson(
                    null, "U3", VehicleType.MOTO, Instant.now()
            );
            DeliveryPersonJpaEntity savedEntity = new DeliveryPersonJpaEntity(
                    10L, "U3", true, Instant.now()
            );
            when(jpaRepository.save(any(DeliveryPersonJpaEntity.class))).thenReturn(savedEntity);

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            DeliveryPerson result = adapter.save(person);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getUserId()).isEqualTo("U3");
            verify(jpaRepository, times(1)).save(any(DeliveryPersonJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("existsByUserId")
    class ExistsByUserId {

        @Test
        @DisplayName("TC6: returns true when userId exists")
        void returnsTrueWhenExists() {
            when(jpaRepository.findByUserId("U1")).thenReturn(Optional.of(
                    new DeliveryPersonJpaEntity(1L, "U1", true, Instant.now())
            ));

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            assertThat(adapter.existsByUserId("U1")).isTrue();
        }

        @Test
        @DisplayName("TC7: returns false when userId does not exist")
        void returnsFalseWhenNotExists() {
            when(jpaRepository.findByUserId("U999")).thenReturn(Optional.empty());

            JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpaRepository);
            assertThat(adapter.existsByUserId("U999")).isFalse();
        }
    }
}
