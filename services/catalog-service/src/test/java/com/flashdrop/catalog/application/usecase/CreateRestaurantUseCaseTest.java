package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnerAlreadyAssignedException;
import com.flashdrop.catalog.domain.model.Restaurant;

class CreateRestaurantUseCaseTest {

    private final RestaurantRepositoryPort restaurants = mock(RestaurantRepositoryPort.class);
    private final CreateRestaurantUseCase useCase = new CreateRestaurantUseCase(restaurants);

    private static Restaurant stored(Long owner) {
        return new Restaurant(9L, owner, "Local", "Calle 1", LocalDateTime.now(), false, List.of());
    }

    @Test
    void execute_withNameAndOwner_createsAClosedRestaurant() {
        when(restaurants.findByUserId(5L)).thenReturn(Optional.empty());
        when(restaurants.create("Local", "Calle 1", 5L, false)).thenReturn(stored(5L));

        Restaurant result = useCase.execute("  Local ", " Calle 1 ", 5L);

        assertThat(result.getId()).isEqualTo(9L);
        verify(restaurants).create("Local", "Calle 1", 5L, false);
    }

    @Test
    void execute_withoutOwner_createsWithoutCheckingOwnership() {
        when(restaurants.create("Local", null, null, false)).thenReturn(stored(null));

        useCase.execute("Local", "  ", null);

        verify(restaurants, never()).findByUserId(any());
    }

    @Test
    void execute_withBlankName_isRejected() {
        assertThatThrownBy(() -> useCase.execute("  ", "Calle", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurants, never()).create(any(), any(), any(), anyBoolean());
    }

    @Test
    void execute_withNonPositiveOwner_isRejected() {
        assertThatThrownBy(() -> useCase.execute("Local", "Calle", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_whenOwnerAlreadyHasARestaurant_isConflict() {
        when(restaurants.findByUserId(eq(5L))).thenReturn(Optional.of(stored(5L)));

        assertThatThrownBy(() -> useCase.execute("Otro", "Calle", 5L))
                .isInstanceOf(OwnerAlreadyAssignedException.class);
        verify(restaurants, never()).create(any(), any(), any(), anyBoolean());
    }
}
