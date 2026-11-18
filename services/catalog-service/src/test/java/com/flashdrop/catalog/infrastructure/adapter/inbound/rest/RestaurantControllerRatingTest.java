package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flashdrop.catalog.application.usecase.GetRestaurantRatingStatsUseCase;
import com.flashdrop.catalog.application.usecase.ListRestaurantsUseCase;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.Restaurant;

/**
 * El marketplace muestra el promedio de calificacion del local (T14).
 *
 * <p>El promedio sale de la cache local, no de una llamada en linea a
 * reviews-service: cuando no hay dato cacheado el local aparece sin
 * promedio y con cero calificaciones.
 */
@DisplayName("RestaurantController — ratings en el listado del marketplace")
class RestaurantControllerRatingTest {

    private ListRestaurantsUseCase listRestaurantsUseCase;
    private GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listRestaurantsUseCase = mock(ListRestaurantsUseCase.class);
        getRestaurantRatingStatsUseCase = mock(GetRestaurantRatingStatsUseCase.class);
        RestaurantController controller = new RestaurantController(
                listRestaurantsUseCase,
                getRestaurantRatingStatsUseCase,
                Clock.fixed(LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static Restaurant restaurant(Long id) {
        return new Restaurant(id, id, "Local " + id, "Calle " + id, LocalDateTime.now());
    }

    @Test
    @DisplayName("el listado expone el promedio cacheado y la cantidad de calificaciones")
    void listing_exposesTheCachedAverageAndCount() throws Exception {
        when(listRestaurantsUseCase.execute()).thenReturn(List.of(restaurant(1L)));
        when(getRestaurantRatingStatsUseCase.execute(List.of(1L)))
                .thenReturn(Map.of(1L, new RatingSummary(4.5, 12L)));

        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].ratingAverage").value(4.5))
                .andExpect(jsonPath("$[0].ratingCount").value(12));
    }

    @Test
    @DisplayName("un local sin calificaciones aparece sin promedio y con cero calificaciones")
    void listing_withoutCachedRating_exposesNullAverageAndZeroCount() throws Exception {
        when(listRestaurantsUseCase.execute()).thenReturn(List.of(restaurant(7L)));
        when(getRestaurantRatingStatsUseCase.execute(List.of(7L))).thenReturn(Map.of());

        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].ratingAverage").value(nullValue()))
                .andExpect(jsonPath("$[0].ratingCount").value(0));
    }
}
