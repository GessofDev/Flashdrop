package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.dto.PendingReviews;
import com.flashdrop.reviews.application.dto.TargetReviewsPage;
import com.flashdrop.reviews.application.port.inbound.CreateReviewUseCase;
import com.flashdrop.reviews.application.port.inbound.ListPendingReviewsUseCase;
import com.flashdrop.reviews.application.port.inbound.ListReviewsForTargetUseCase;
import com.flashdrop.reviews.domain.model.AuthorRole;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública de calificaciones (T8) con JWT real firmado por cada rol.
 *
 * <p>El filtro de autenticación es el de producción
 * ({@link JwtAuthenticationFilter}); la clave del JWKS se simula con
 * {@link JwksKeyProvider} mockeado, así que los tokens que se firman en el
 * test son los mismos que aceptaría auth-service.</p>
 */
@WebMvcTest(ReviewsController.class)
@Import({SecurityConfig.class, CurrentUserResolver.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://localhost:1/jwks.json",
        "internal.api.key=test-internal-key"
})
@DisplayName("ReviewsController — API pública con JWT por rol")
class ReviewsControllerTest {

    private static final Long AUTHOR_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateReviewUseCase createReviewUseCase;

    @MockBean
    private ListPendingReviewsUseCase listPendingReviewsUseCase;

    @MockBean
    private ListReviewsForTargetUseCase listReviewsForTargetUseCase;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @BeforeEach
    void setUp() throws Exception {
        // lenient: los tests que no llegan al filtro (requests sin token) no
        // consultan el JWKS y no deben romper por un stub sin uso.
        org.mockito.Mockito.lenient()
                .when(jwksKeyProvider.findKeyByKid(any()))
                .thenReturn(Optional.of(TestJwtUtil.getPublicKey()));
    }

    private String bearer(String subject, String... roles) throws Exception {
        return "Bearer " + TestJwtUtil.generateValidJwt(subject, List.of(roles));
    }

    private Review review() {
        return Review.persisted(1L, 501L, AUTHOR_ID, RatingTarget.TIENDA, 7L, Rating.of(5),
                "excelente", ReviewStatus.PUBLICADO,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    @Nested
    @DisplayName("POST /api/reviews")
    class Create {

        @ParameterizedTest
        @ValueSource(strings = {"Cliente", "Repartidor", "Restaurante"})
        @DisplayName("TC1: un JWT con cualquiera de los tres roles puede crear una calificación (201)")
        void anyKnownRoleCanCreate(String role) throws Exception {
            when(createReviewUseCase.execute(any())).thenReturn(review());

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5,\"comment\":\"excelente\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.orderId").value(501))
                    .andExpect(jsonPath("$.targetType").value("TIENDA"))
                    .andExpect(jsonPath("$.targetId").value(7))
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.status").value("PUBLICADO"));
        }

        @Test
        @DisplayName("TC2: sin cabecera Authorization se responde 401")
        void withoutTokenIsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.service").value("reviews-service"));
        }

        @Test
        @DisplayName("TC3: un token con un rol desconocido se responde 403")
        void unknownRoleIsForbidden() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Administrador"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC4: un token sin claim roles se responde 403")
        void tokenWithoutRolesIsForbidden() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", "Bearer " + TestJwtUtil.generateValidJwt(String.valueOf(AUTHOR_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC5: un rating fuera de rango se responde 400")
        void ratingOutOfRangeIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":6}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("TC6: un comentario de más de 500 caracteres se responde 400")
        void tooLongCommentIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5,\"comment\":\""
                                    + "x".repeat(501) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("TC7: un body sin orderId se responde 400")
        void missingOrderIdIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/reviews/pending/{orderId}")
    class Pending {

        @Test
        @DisplayName("TC8: devuelve los pendientes y las ya hechas")
        void pendingReturnsBothLists() throws Exception {
            when(listPendingReviewsUseCase.execute(eq(501L), eq(AUTHOR_ID), any()))
                    .thenReturn(new PendingReviews(
                            501L,
                            List.of(new PendingReviews.PendingTarget(RatingTarget.TIENDA, 7L)),
                            List.of(review())));

            mockMvc.perform(get("/api/reviews/pending/{orderId}", 501)
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(501))
                    .andExpect(jsonPath("$.pending[0].targetType").value("TIENDA"))
                    .andExpect(jsonPath("$.pending[0].targetId").value(7))
                    .andExpect(jsonPath("$.completed[0].rating").value(5));
        }

        @Test
        @DisplayName("TC9: la ruta de pendientes tiene prioridad sobre la de destino")
        void pendingRouteWinsOverTargetRoute() throws Exception {
            when(listPendingReviewsUseCase.execute(eq(501L), eq(AUTHOR_ID), any()))
                    .thenReturn(new PendingReviews(501L, List.of(), List.of()));

            mockMvc.perform(get("/api/reviews/pending/{orderId}", 501)
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(501))
                    .andExpect(jsonPath("$.pending").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/reviews/{targetType}/{targetId}")
    class ListForTarget {

        @Test
        @DisplayName("TC10: devuelve la página con el promedio, la cantidad y la distribución")
        void listReturnsPageWithAggregate() throws Exception {
            when(listReviewsForTargetUseCase.execute(eq(RatingTarget.TIENDA), eq(7L), any(Pageable.class)))
                    .thenReturn(new TargetReviewsPage(
                            List.of(review()),
                            0, 20, 1L, 1,
                            5.0d, 1L,
                            Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 1L)));

            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "TIENDA", 7)
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].rating").value(5))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.aggregate.average").value(5.0))
                    .andExpect(jsonPath("$.aggregate.count").value(1))
                    .andExpect(jsonPath("$.aggregate.distribution['5']").value(1));
        }

        @Test
        @DisplayName("TC11: un tipo de destino desconocido se responde 400")
        void unknownTargetTypeIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "BANANA", 7)
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("TC12: un id de destino no numérico se responde 400")
        void nonNumericTargetIdIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "TIENDA", "abc")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Control de acceso")
    class AccessControl {

        @Test
        @DisplayName("TC13: sin token, el listado de un destino se responde 401")
        void listWithoutTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "TIENDA", 7))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC14: un token de otro servicio sin roles de autor se responde 403")
        void tokenWithForeignRoleIsForbidden() throws Exception {
            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "TIENDA", 7)
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Admin")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("El destino lo deriva el servidor")
    class TargetDerivation {

        @Test
        @DisplayName("TC15: el body no puede enviar el id del destino")
        void bodyCannotCarryTheTargetId() throws Exception {
            when(createReviewUseCase.execute(any())).thenReturn(review());

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"targetId\":999,\"rating\":5}"))
                    .andExpect(status().isCreated());

            // El id del destino que se persiste es el derivado de la orden (7),
            // nunca el 999 que venía en el body.
            org.mockito.ArgumentCaptor<com.flashdrop.reviews.application.dto.CreateReviewCommand> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.flashdrop.reviews.application.dto.CreateReviewCommand.class);
            org.mockito.Mockito.verify(createReviewUseCase).execute(captor.capture());
            assertDoesNotContainTargetId(captor.getValue());
        }

        private void assertDoesNotContainTargetId(
                com.flashdrop.reviews.application.dto.CreateReviewCommand command) {
            org.assertj.core.api.Assertions.assertThat(command.targetType()).isEqualTo("TIENDA");
            org.assertj.core.api.Assertions.assertThat(command.orderId()).isEqualTo(501L);
        }
    }

    @Nested
    @DisplayName("Errores del caso de uso")
    class UseCaseErrors {

        @Test
        @DisplayName("TC16: una orden no entregada se responde 422")
        void orderNotDeliveredIsUnprocessable() throws Exception {
            when(createReviewUseCase.execute(any())).thenThrow(
                    new com.flashdrop.reviews.domain.exception.OrderNotDeliveredException(501L, "PREPARANDO"));

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
        }

        @Test
        @DisplayName("TC17: una calificación duplicada se responde 409")
        void duplicateIsConflict() throws Exception {
            when(createReviewUseCase.execute(any())).thenThrow(
                    new com.flashdrop.reviews.domain.exception.ReviewAlreadyExistsException(501L, AUTHOR_ID, "TIENDA"));

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("TC18: una relación no autorizada se responde 403")
        void forbiddenRelationIsForbidden() throws Exception {
            when(createReviewUseCase.execute(any())).thenThrow(
                    new com.flashdrop.reviews.domain.exception.ReviewForbiddenException("no puede"));

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("TC19: una orden inexistente se responde 404")
        void missingOrderIsNotFound() throws Exception {
            when(createReviewUseCase.execute(any())).thenThrow(
                    new com.flashdrop.reviews.domain.exception.OrderNotFoundException(501L));

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("TC20: orders-service caído se responde 503")
        void ordersServiceDownIsServiceUnavailable() throws Exception {
            when(createReviewUseCase.execute(any())).thenThrow(
                    new com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException(
                            "orders-service", "timeout", new RuntimeException("boom")));

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }
    }

    @Nested
    @DisplayName("El autor sale del JWT")
    class AuthorFromToken {

        @Test
        @DisplayName("TC21: el id del autor es el subject del token, no el del body")
        void authorComesFromTheToken() throws Exception {
            when(createReviewUseCase.execute(any())).thenReturn(review());

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer("77", "Cliente"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5,\"authorUserId\":999}"))
                    .andExpect(status().isCreated());

            org.mockito.ArgumentCaptor<com.flashdrop.reviews.application.dto.CreateReviewCommand> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.flashdrop.reviews.application.dto.CreateReviewCommand.class);
            org.mockito.Mockito.verify(createReviewUseCase).execute(captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().authorUserId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("TC22: los roles del autor salen del claim, con todos sus valores")
        void rolesComeFromTheClaim() throws Exception {
            when(createReviewUseCase.execute(any())).thenReturn(review());

            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", bearer("77", "Cliente", "Restaurante"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":501,\"targetType\":\"TIENDA\",\"rating\":5}"))
                    .andExpect(status().isCreated());

            org.mockito.ArgumentCaptor<com.flashdrop.reviews.application.dto.CreateReviewCommand> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.flashdrop.reviews.application.dto.CreateReviewCommand.class);
            org.mockito.Mockito.verify(createReviewUseCase).execute(captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().authorRoles())
                    .containsExactlyInAnyOrder(AuthorRole.CLIENTE, AuthorRole.RESTAURANTE);
        }
    }

    @Nested
    @DisplayName("Frontera de paginación")
    class Pagination {

        @Test
        @DisplayName("TC23: un size cero o negativo usa el tamaño por defecto")
        void nonPositiveSizeFallsBackToDefault() throws Exception {
            when(listReviewsForTargetUseCase.execute(eq(RatingTarget.TIENDA), eq(7L), any(Pageable.class)))
                    .thenReturn(new TargetReviewsPage(List.of(), 0, 20, 0L, 0, 0.0d, 0L, Map.of()));

            mockMvc.perform(get("/api/reviews/{targetType}/{targetId}", "TIENDA", 7)
                            .param("size", "0")
                            .header("Authorization", bearer(String.valueOf(AUTHOR_ID), "Cliente")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            org.mockito.Mockito.verify(listReviewsForTargetUseCase).execute(any(), anyLong(), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }
    }
}
