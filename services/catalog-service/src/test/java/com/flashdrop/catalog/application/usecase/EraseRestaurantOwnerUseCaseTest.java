package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;

class EraseRestaurantOwnerUseCaseTest {

    private final RestaurantRepositoryPort restaurants = mock(RestaurantRepositoryPort.class);
    private final EraseRestaurantOwnerUseCase useCase = new EraseRestaurantOwnerUseCase(restaurants);

    @Test
    void execute_ownerWithRestaurant_unlinksIt() {
        when(restaurants.clearOwner(7L)).thenReturn(Optional.of(
                new Restaurant(1L, null, "Local", "Calle", LocalDateTime.now(), false, List.of())));

        assertThat(useCase.execute(7L)).isTrue();
    }

    @Test
    void execute_userWithoutRestaurant_isANoOp() {
        when(restaurants.clearOwner(8L)).thenReturn(Optional.empty());

        assertThat(useCase.execute(8L)).isFalse();
    }

    @Test
    void execute_invalidUser_isRejected() {
        assertThatThrownBy(() -> useCase.execute(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
