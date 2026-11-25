package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

class SetStoreLocationUseCaseTest {

    private final RestaurantRepositoryPort restaurants = mock(RestaurantRepositoryPort.class);
    private final SetStoreLocationUseCase useCase = new SetStoreLocationUseCase(restaurants);

    private static Restaurant restaurant(BigDecimal lat, BigDecimal lng) {
        return new Restaurant(9L, 5L, "Local", "Calle 1", LocalDateTime.now(), true, List.of(), lat, lng);
    }

    @Test
    void execute_validCoordinates_updatesStoreLocation() {
        BigDecimal lat = new BigDecimal("-33.448900");
        BigDecimal lng = new BigDecimal("-70.669300");
        when(restaurants.updateLocation(9L, lat, lng)).thenReturn(Optional.of(restaurant(lat, lng)));

        Restaurant updated = useCase.execute(9L, lat, lng);

        assertThat(updated.getLatitude()).isEqualByComparingTo(lat);
        assertThat(updated.getLongitude()).isEqualByComparingTo(lng);
        verify(restaurants).updateLocation(9L, lat, lng);
    }

    @Test
    void execute_bothNull_clearsStoreLocation() {
        when(restaurants.updateLocation(9L, null, null)).thenReturn(Optional.of(restaurant(null, null)));

        Restaurant updated = useCase.execute(9L, null, null);

        assertThat(updated.getLatitude()).isNull();
        assertThat(updated.getLongitude()).isNull();
        verify(restaurants).updateLocation(9L, null, null);
    }

    @Test
    void execute_halfPair_rejectedBeforeLookup() {
        assertThatThrownBy(() -> useCase.execute(9L, new BigDecimal("-33.45"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurants, never()).updateLocation(any(), any(), any());
    }

    @Test
    void execute_outOfRange_rejectedBeforeLookup() {
        assertThatThrownBy(() -> useCase.execute(9L, new BigDecimal("95.0"), new BigDecimal("-70.66")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurants, never()).updateLocation(any(), any(), any());
    }

    @Test
    void execute_missingRestaurantOnUpdate_throwsNotFound() {
        BigDecimal lat = new BigDecimal("-33.45");
        BigDecimal lng = new BigDecimal("-70.66");
        when(restaurants.updateLocation(9L, lat, lng)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(9L, lat, lng))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
