package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.usecase.GetDeliveryPersonRatingStatsUseCase;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import com.flashdrop.observability.config.ObservabilityAutoConfiguration;

/**
 * El perfil del repartidor expone su promedio de calificacion (T15).
 *
 * <p>El promedio sale de la cache local (pull a reviews-service), no de una
 * llamada en linea: cuando no hay dato cacheado el repartidor aparece sin
 * promedio y con cero calificaciones.
 */
@WebMvcTest(InternalDeliveryPersonsController.class)
@Import(ObservabilityAutoConfiguration.class)
@TestPropertySource(properties = {
        "internal.api.key=test-internal-key",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@DisplayName("InternalDeliveryPersonsController — promedio de calificacion del repartidor")
class DeliveryPersonRatingProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    @MockBean
    private GetDeliveryPersonRatingStatsUseCase getDeliveryPersonRatingStatsUseCase;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    @Test
    @DisplayName("el perfil expone el promedio cacheado y la cantidad de calificaciones")
    void profile_exposesTheCachedAverageAndCount() throws Exception {
        when(deliveryPersonRepository.findByUserId("33")).thenReturn(Optional.of(
                new DeliveryPerson(1L, "33", VehicleType.MOTO, Instant.parse("2024-01-01T10:00:00Z"))));
        when(getDeliveryPersonRatingStatsUseCase.executeForUserId("33"))
                .thenReturn(new RatingSummary(4.5, 12L));

        mockMvc.perform(get("/api/internal/delivery-persons")
                        .param("userId", "33")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingAverage").value(4.5))
                .andExpect(jsonPath("$.data.ratingCount").value(12));
    }

    @Test
    @DisplayName("un repartidor sin calificaciones aparece sin promedio y con cero calificaciones")
    void profile_withoutCachedRating_exposesNullAverageAndZeroCount() throws Exception {
        when(deliveryPersonRepository.findByUserId("44")).thenReturn(Optional.of(
                new DeliveryPerson(2L, "44", VehicleType.MOTO, Instant.parse("2024-01-01T10:00:00Z"))));
        when(getDeliveryPersonRatingStatsUseCase.executeForUserId("44"))
                .thenReturn(RatingSummary.none());

        mockMvc.perform(get("/api/internal/delivery-persons")
                        .param("userId", "44")
                        .header(INTERNAL_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingAverage").value(nullValue()))
                .andExpect(jsonPath("$.data.ratingCount").value(0));
    }
}
