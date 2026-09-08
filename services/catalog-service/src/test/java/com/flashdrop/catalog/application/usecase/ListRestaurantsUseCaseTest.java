package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;

class ListRestaurantsUseCaseTest {

    private final RestaurantRepositoryPort restaurantRepositoryPort = Mockito.mock(RestaurantRepositoryPort.class);
    private final ListRestaurantsUseCase useCase = new ListRestaurantsUseCase(restaurantRepositoryPort);

    @Test
    void executeReturnsAllRestaurants() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 13, 10, 0);
        when(restaurantRepositoryPort.findAll()).thenReturn(List.of(
                new Restaurant(1L, 10L, "Burgers House", "Los Leones 300", createdAt),
                new Restaurant(2L, 20L, "Pizza Uno", "Av. Providencia 900", createdAt)
        ));

        List<Restaurant> result = useCase.execute();

        assertThat(result)
                .extracting(Restaurant::getName)
                .containsExactly("Burgers House", "Pizza Uno");
    }
}
