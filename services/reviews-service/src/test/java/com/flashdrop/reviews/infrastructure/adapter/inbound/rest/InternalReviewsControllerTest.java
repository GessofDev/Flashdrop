package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.port.inbound.GetRatingAggregatesUseCase;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.config.SecurityConfig;
import com.flashdrop.reviews.infrastructure.security.CurrentUserResolver;
import com.flashdrop.reviews.infrastructure.security.JwtAuthenticationFilter;
import com.flashdrop.reviews.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API interna de agregados (T9).
 *
 * <p>El contrato {@code {"items":[{"targetId","average","count"}]} lo consumen
 * catalog-service y delivery-service: no se cambia sin avisar.</p>
 */
@WebMvcTest(InternalReviewsController.class)
@Import({SecurityConfig.class, CurrentUserResolver.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://localhost:1/jwks.json",
        "internal.api.key=test-internal-key"
})
@DisplayName("InternalReviewsController — agregados por lotes")
class InternalReviewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetRatingAggregatesUseCase getRatingAggregatesUseCase;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @BeforeEach
    void setUp() {
        // El filtro de JWT no intercepta /api/internal/**: no hace falta JWKS.
    }

    @Test
    @DisplayName("TC1: devuelve un agregado por cada id solicitado, en el orden pedido")
    void returnsOneItemPerRequestedId() throws Exception {
        when(getRatingAggregatesUseCase.execute(eq(RatingTarget.TIENDA), eq(List.of(1L, 2L, 3L))))
                .thenReturn(List.of(
                        new TargetRatingStats(1L, 4.5d, 10L, Map.of(5, 6L)),
                        new TargetRatingStats(2L, 3.0d, 4L, Map.of(3, 4L)),
                        new TargetRatingStats(3L, 0.0d, 0L, Map.of())));

        mockMvc.perform(get("/api/internal/reviews/aggregates")
                        .param("targetType", "TIENDA")
                        .param("ids", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].targetId").value(1))
                .andExpect(jsonPath("$.items[0].average").value(4.5))
                .andExpect(jsonPath("$.items[0].count").value(10))
                .andExpect(jsonPath("$.items[2].targetId").value(3))
                .andExpect(jsonPath("$.items[2].count").value(0));
    }

    @Test
    @DisplayName("TC2: la ruta interna no exige JWT (la protege la X-Internal-Api-Key)")
    void internalPathDoesNotRequireJwt() throws Exception {
        when(getRatingAggregatesUseCase.execute(eq(RatingTarget.REPARTIDOR), eq(List.of(9L))))
                .thenReturn(List.of(new TargetRatingStats(9L, 5.0d, 2L, Map.of(5, 2L))));

        mockMvc.perform(get("/api/internal/reviews/aggregates")
                        .param("targetType", "REPARTIDOR")
                        .param("ids", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetId").value(9));
    }

    @Test
    @DisplayName("TC3: un tipo de destino desconocido se responde 400")
    void unknownTargetTypeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/internal/reviews/aggregates")
                        .param("targetType", "BANANA")
                        .param("ids", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TC4: un id no numérico se responde 400")
    void nonNumericIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/internal/reviews/aggregates")
                        .param("targetType", "TIENDA")
                        .param("ids", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC5: el caso de uso recibe los ids en el orden pedido")
    void idsArePassedInOrder() throws Exception {
        when(getRatingAggregatesUseCase.execute(eq(RatingTarget.TIENDA), eq(List.of(3L, 1L))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/internal/reviews/aggregates")
                        .param("targetType", "TIENDA")
                        .param("ids", "3, 1"))
                .andExpect(status().isOk());

        verify(getRatingAggregatesUseCase).execute(RatingTarget.TIENDA, List.of(3L, 1L));
    }
}
