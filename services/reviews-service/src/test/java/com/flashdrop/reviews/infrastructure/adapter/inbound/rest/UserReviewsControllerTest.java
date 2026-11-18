package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.dto.MyReviewsPage;
import com.flashdrop.reviews.application.port.inbound.ListMyReviewsUseCase;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.config.SecurityConfig;
import com.flashdrop.reviews.infrastructure.security.CurrentUserResolver;
import com.flashdrop.reviews.infrastructure.security.JwtAuthenticationFilter;
import com.flashdrop.reviews.infrastructure.security.JwksKeyProvider;
import com.flashdrop.reviews.infrastructure.security.TestJwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historial de calificaciones del autor (T8).
 */
@WebMvcTest(UserReviewsController.class)
@Import({SecurityConfig.class, CurrentUserResolver.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://localhost:1/jwks.json",
        "internal.api.key=test-internal-key"
})
@DisplayName("UserReviewsController — historial del autor")
class UserReviewsControllerTest {

    private static final Long AUTHOR_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListMyReviewsUseCase listMyReviewsUseCase;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @BeforeEach
    void setUp() throws Exception {
        // lenient: el test sin token no consulta el JWKS.
        org.mockito.Mockito.lenient()
                .when(jwksKeyProvider.findKeyByKid(any()))
                .thenReturn(Optional.of(TestJwtUtil.getPublicKey()));
    }

    private Review review() {
        return Review.persisted(1L, 501L, AUTHOR_ID, RatingTarget.REPARTIDOR, 42L, Rating.of(4),
                "rápido", ReviewStatus.PUBLICADO,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    @DisplayName("TC1: devuelve el historial del autor autenticado")
    void returnsMyReviews() throws Exception {
        when(listMyReviewsUseCase.execute(eq(AUTHOR_ID), any(Pageable.class)))
                .thenReturn(new MyReviewsPage(List.of(review()), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/users/me/reviews")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateValidJwt(
                                String.valueOf(AUTHOR_ID), List.of("Cliente"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].targetType").value("REPARTIDOR"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("TC2: el historial es del subject del token")
    void usesTheTokenSubject() throws Exception {
        when(listMyReviewsUseCase.execute(eq(77L), any(Pageable.class)))
                .thenReturn(new MyReviewsPage(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/users/me/reviews")
                        .header("Authorization", "Bearer " + TestJwtUtil.generateValidJwt(
                                "77", List.of("Repartidor"))))
                .andExpect(status().isOk());

        verify(listMyReviewsUseCase).execute(eq(77L), any(Pageable.class));
    }

    @Test
    @DisplayName("TC3: sin token se responde 401")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me/reviews"))
                .andExpect(status().isUnauthorized());
    }
}
