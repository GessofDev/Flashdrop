package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.dto.CreateReviewCommand;
import com.flashdrop.reviews.application.port.outbound.OrderLookupPort;
import com.flashdrop.reviews.application.port.outbound.RestaurantLookupPort;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException;
import com.flashdrop.reviews.domain.exception.InvalidCommentException;
import com.flashdrop.reviews.domain.exception.InvalidRatingException;
import com.flashdrop.reviews.domain.exception.InvalidTargetTypeException;
import com.flashdrop.reviews.domain.exception.OrderNotDeliveredException;
import com.flashdrop.reviews.domain.exception.OrderNotFoundException;
import com.flashdrop.reviews.domain.exception.ReviewAlreadyExistsException;
import com.flashdrop.reviews.domain.exception.ReviewForbiddenException;
import com.flashdrop.reviews.domain.exception.TargetNotFoundException;
import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.model.OrderSnapshot;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caso de uso de creación de calificaciones (T7).
 *
 * <p>Cubre la derivación del destino desde la orden (S7), la idempotencia
 * (S6), la matriz de autorización §4 con la participación en la orden (S4) y la
 * degradación segura cuando orders-service no responde.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateReviewUseCaseImpl — creación de calificaciones")
class CreateReviewUseCaseImplTest {

    private static final Long ORDER_ID = 501L;
    private static final Long CLIENT_ID = 10L;
    private static final Long RESTAURANT_ID = 7L;
    private static final Long COURIER_ID = 42L;

    @Mock
    private ReviewRepositoryPort reviewRepository;

    @Mock
    private OrderLookupPort orderLookup;

    @Mock
    private RestaurantLookupPort restaurantLookup;

    private CreateReviewUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        useCase = new CreateReviewUseCaseImpl(reviewRepository, orderLookup, restaurantLookup, clock);
    }

    private OrderSnapshot deliveredOrder() {
        return new OrderSnapshot(ORDER_ID, "ENTREGADO", CLIENT_ID, RESTAURANT_ID, COURIER_ID);
    }

    private CreateReviewCommand command(String targetType, int rating, Set<AuthorRole> roles) {
        return new CreateReviewCommand(ORDER_ID, targetType, rating, "bueno", CLIENT_ID, roles);
    }

    private void givenOrder(OrderSnapshot order) {
        when(orderLookup.findOrder(ORDER_ID)).thenReturn(order);
    }

    private void givenNoExistingReview() {
        when(reviewRepository.existsByOrderIdAndAuthorUserIdAndTargetType(anyLong(), anyLong(), any()))
                .thenReturn(false);
    }

    private void givenSavedReview() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return Review.persisted(1L, review.getOrderId(), review.getAuthorUserId(),
                    review.getTargetType(), review.getTargetId(), review.getRating(),
                    review.getComment(), review.getStatus(), review.getCreatedAt(), review.getUpdatedAt());
        });
    }

    @Nested
    @DisplayName("Derivación del destino (S7)")
    class TargetDerivation {

        @Test
        @DisplayName("TC1: el id del destino lo deriva el servidor desde la orden, no del body")
        void targetIdIsDerivedFromTheOrder() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            Review review = useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE)));

            assertThat(review.getTargetId()).isEqualTo(RESTAURANT_ID);
            assertThat(review.getTargetType()).isEqualTo(RatingTarget.TIENDA);
            assertThat(review.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(review.getAuthorUserId()).isEqualTo(CLIENT_ID);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLICADO);
        }

        @Test
        @DisplayName("TC2: el destino REPARTIDOR se deriva del repartidor de la orden")
        void courierTargetIsDerivedFromTheOrder() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            Review review = useCase.execute(command("REPARTIDOR", 4, Set.of(AuthorRole.CLIENTE)));

            assertThat(review.getTargetId()).isEqualTo(COURIER_ID);
        }

        @Test
        @DisplayName("TC3: si la orden no tiene repartidor, el destino REPARTIDOR no existe (404)")
        void courierTargetWithoutCourierIsNotFound() {
            givenOrder(new OrderSnapshot(ORDER_ID, "ENTREGADO", CLIENT_ID, RESTAURANT_ID, null));

            assertThatThrownBy(() -> useCase.execute(command("REPARTIDOR", 4, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(TargetNotFoundException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }
    }

    @Nested
    @DisplayName("Idempotencia (S6)")
    class Idempotency {

        @Test
        @DisplayName("TC4: si ya existe una calificación para (orden, autor, destino) se devuelve 409")
        void duplicateIsRejected() {
            givenOrder(deliveredOrder());
            when(reviewRepository.existsByOrderIdAndAuthorUserIdAndTargetType(
                    ORDER_ID, CLIENT_ID, RatingTarget.TIENDA)).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(ReviewAlreadyExistsException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC5: el reintento no crea una fila nueva")
        void retryDoesNotCreateASecondRow() {
            givenOrder(deliveredOrder());
            when(reviewRepository.existsByOrderIdAndAuthorUserIdAndTargetType(
                    ORDER_ID, CLIENT_ID, RatingTarget.TIENDA)).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(ReviewAlreadyExistsException.class);

            verify(reviewRepository, never()).save(any(Review.class));
        }
    }

    @Nested
    @DisplayName("Autorización (S4)")
    class Authorization {

        @Test
        @DisplayName("TC6: el cliente puede calificar la tienda de su orden")
        void clientCanRateTheStore() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            Review review = useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE)));

            assertThat(review.getTargetId()).isEqualTo(RESTAURANT_ID);
        }

        @Test
        @DisplayName("TC7: el cliente NO puede calificar a otro cliente (403)")
        void clientCannotRateAClient() {
            givenOrder(deliveredOrder());

            assertThatThrownBy(() -> useCase.execute(command("CLIENTE", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(ReviewForbiddenException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC8: un rol válido que no participa de la orden recibe 403")
        void validRoleButNotParticipantIsRejected() {
            // El autor es cliente de otra orden: su id no es el clientId de esta.
            givenOrder(new OrderSnapshot(ORDER_ID, "ENTREGADO", 99L, RESTAURANT_ID, COURIER_ID));

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(ReviewForbiddenException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC9: el dueño de la tienda puede calificar al repartidor de su orden")
        void storeOwnerCanRateTheCourier() {
            givenOrder(deliveredOrder());
            when(restaurantLookup.findRestaurantIdByOwner(CLIENT_ID)).thenReturn(Optional.of(RESTAURANT_ID));
            givenNoExistingReview();
            givenSavedReview();

            Review review = useCase.execute(command("REPARTIDOR", 3, Set.of(AuthorRole.RESTAURANTE)));

            assertThat(review.getTargetId()).isEqualTo(COURIER_ID);
        }

        @Test
        @DisplayName("TC10: el dueño de otra tienda no puede calificar en esta orden (403)")
        void ownerOfAnotherStoreIsRejected() {
            givenOrder(deliveredOrder());
            when(restaurantLookup.findRestaurantIdByOwner(CLIENT_ID)).thenReturn(Optional.of(999L));

            assertThatThrownBy(() -> useCase.execute(command("REPARTIDOR", 3, Set.of(AuthorRole.RESTAURANTE))))
                    .isInstanceOf(ReviewForbiddenException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC11: con claim multi-rol basta con que uno de los roles habilite la relación")
        void multiRoleTokenUsesAnyValidRole() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            // El autor es el cliente de la orden: el rol CLIENTE habilita TIENDA
            // aunque el token también traiga Repartidor y Restaurante.
            Review review = useCase.execute(command("TIENDA", 4,
                    EnumSet.of(AuthorRole.CLIENTE, AuthorRole.REPARTIDOR, AuthorRole.RESTAURANTE)));

            assertThat(review.getTargetId()).isEqualTo(RESTAURANT_ID);
            verify(restaurantLookup, never()).findRestaurantIdByOwner(anyLong());
        }

        @Test
        @DisplayName("TC12: sin roles en el claim no se puede calificar (403)")
        void withoutRolesIsForbidden() {
            givenOrder(deliveredOrder());

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of())))
                    .isInstanceOf(ReviewForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Estado de la orden (S3) y errores de entrada")
    class OrderStateAndValidation {

        @Test
        @DisplayName("TC13: una orden no entregada se rechaza con 422")
        void notDeliveredIsRejected() {
            givenOrder(new OrderSnapshot(ORDER_ID, "CANCELADO", CLIENT_ID, RESTAURANT_ID, COURIER_ID));

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(OrderNotDeliveredException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC14: una orden inexistente se propaga como 404")
        void missingOrderIsNotFound() {
            when(orderLookup.findOrder(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("TC15: si orders-service no responde, la calificación falla con 503")
        void ordersServiceDownFailsClosed() {
            when(orderLookup.findOrder(ORDER_ID)).thenThrow(new InternalServiceUnavailableException(
                    "orders-service", "timeout", new RuntimeException("boom")));

            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(InternalServiceUnavailableException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("TC16: un rating fuera de rango se rechaza (400)")
        void outOfRangeRatingIsRejected() {
            assertThatThrownBy(() -> useCase.execute(command("TIENDA", 6, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(InvalidRatingException.class);
            verify(orderLookup, never()).findOrder(anyLong());
        }

        @Test
        @DisplayName("TC17: un tipo de destino desconocido se rechaza (400)")
        void unknownTargetTypeIsRejected() {
            assertThatThrownBy(() -> useCase.execute(command("BANANA", 5, Set.of(AuthorRole.CLIENTE))))
                    .isInstanceOf(InvalidTargetTypeException.class);
            verify(orderLookup, never()).findOrder(anyLong());
        }

        @Test
        @DisplayName("TC18: un comentario de más de 500 caracteres se rechaza (400)")
        void tooLongCommentIsRejected() {
            CreateReviewCommand longComment = new CreateReviewCommand(
                    ORDER_ID, "TIENDA", 5, "x".repeat(501), CLIENT_ID, Set.of(AuthorRole.CLIENTE));

            assertThatThrownBy(() -> useCase.execute(longComment))
                    .isInstanceOf(InvalidCommentException.class);
            verify(orderLookup, never()).findOrder(anyLong());
        }
    }

    @Nested
    @DisplayName("Sanitización del comentario")
    class CommentSanitization {

        @Test
        @DisplayName("TC19: el comentario se guarda sin caracteres de control y recortado")
        void commentIsSanitized() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            CreateReviewCommand withControlChars = new CreateReviewCommand(
                    ORDER_ID, "TIENDA", 5, "  hola mundo  ", CLIENT_ID, Set.of(AuthorRole.CLIENTE));

            useCase.execute(withControlChars);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertThat(captor.getValue().getComment()).isEqualTo("hola mundo");
        }

        @Test
        @DisplayName("TC20: un comentario vacío se guarda como null")
        void blankCommentBecomesNull() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            CreateReviewCommand blank = new CreateReviewCommand(
                    ORDER_ID, "TIENDA", 5, "   ", CLIENT_ID, Set.of(AuthorRole.CLIENTE));

            useCase.execute(blank);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertThat(captor.getValue().getComment()).isNull();
        }
    }

    @Nested
    @DisplayName("Auto-calificación")
    class SelfRating {

        @Test
        @DisplayName("TC21: nadie puede calificar a otro actor que es el mismo usuario")
        void selfRatingIsForbidden() {
            // El repartidor de la orden es el propio autor.
            givenOrder(new OrderSnapshot(ORDER_ID, "ENTREGADO", CLIENT_ID, RESTAURANT_ID, CLIENT_ID));

            assertThatThrownBy(() -> useCase.execute(command("REPARTIDOR", 5, Set.of(AuthorRole.REPARTIDOR))))
                    .isInstanceOf(ReviewForbiddenException.class);
            verify(reviewRepository, never()).save(any(Review.class));
        }
    }

    @Nested
    @DisplayName("Persistencia")
    class Persistence {

        @Test
        @DisplayName("TC22: la calificación guardada conserva nota, comentario y destino")
        void savedReviewKeepsTheData() {
            givenOrder(deliveredOrder());
            givenNoExistingReview();
            givenSavedReview();

            CreateReviewCommand full = new CreateReviewCommand(
                    ORDER_ID, "TIENDA", 3, "todo bien", CLIENT_ID, Set.of(AuthorRole.CLIENTE));

            Review saved = useCase.execute(full);

            assertThat(saved.getRating()).isEqualTo(Rating.of(3));
            assertThat(saved.getComment()).isEqualTo("todo bien");
            assertThat(saved.getTargetType()).isEqualTo(RatingTarget.TIENDA);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captor.getValue().getTargetId()).isEqualTo(RESTAURANT_ID);
        }
    }
}
