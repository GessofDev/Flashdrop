package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonJpaEntity;

class JpaDeliveryPersonRepositoryAdapterErasureTest {

    private final JpaDeliveryPersonRepository jpa = mock(JpaDeliveryPersonRepository.class);
    private final JpaDeliveryPersonRepositoryAdapter adapter = new JpaDeliveryPersonRepositoryAdapter(jpa);

    @Test
    void deleteByUserId_existingProfile_isDeleted() {
        DeliveryPersonJpaEntity entity = new DeliveryPersonJpaEntity(5L, "77", true, Instant.now());
        when(jpa.findByUserId("77")).thenReturn(Optional.of(entity));

        assertThat(adapter.deleteByUserId("77")).isTrue();
        verify(jpa).delete(entity);
    }

    @Test
    void deleteByUserId_missingProfile_returnsFalseAndDeletesNothing() {
        when(jpa.findByUserId("99")).thenReturn(Optional.empty());

        assertThat(adapter.deleteByUserId("99")).isFalse();
        verify(jpa, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
