package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.dto.CreateReviewCommand;
import com.flashdrop.reviews.application.port.inbound.CreateReviewUseCase;
import com.flashdrop.reviews.application.port.outbound.OrderLookupPort;
import com.flashdrop.reviews.application.port.outbound.RestaurantLookupPort;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.exception.InvalidCommentException;
import com.flashdrop.reviews.domain.exception.InvalidOrderIdException;
import com.flashdrop.reviews.domain.exception.InvalidRatingException;
import com.flashdrop.reviews.domain.exception.OrderNotDeliveredException;
import com.flashdrop.reviews.domain.exception.ReviewAlreadyExistsException;
import com.flashdrop.reviews.domain.exception.ReviewForbiddenException;
import com.flashdrop.reviews.domain.exception.TargetNotFoundException;
import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.model.OrderSnapshot;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.policy.RatingPolicy;
import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Implementación de {@link CreateReviewUseCase} (T7).
 *
 * <p>Orden de las comprobaciones (cada una con su código HTTP estable):</p>
 * <ol>
 *   <li>Validación de la entrada → 400 (rating fuera de rango, tipo de destino
 *       desconocido, comentario de más de 500 caracteres).</li>
 *   <li>Lectura de la orden → 404 si no existe, 503 si orders-service no responde.</li>
 *   <li>Orden entregada → 422 si no lo está (S3).</li>
 *   <li>Autorización: rol del claim + participación en la orden → 403 (S4).</li>
 *   <li>Derivación del destino desde la orden → 404 si no se puede derivar (S7).</li>
 *   <li>Auto-calificación → 403.</li>
 *   <li>Duplicado → 409 (S6).</li>
 * </ol>
 */
@Service
public class CreateReviewUseCaseImpl implements CreateReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateReviewUseCaseImpl.class);

    /** Longitud máxima del comentario (S5). */
    public static final int MAX_COMMENT_LENGTH = 500;

    /**
     * Orden de evaluación de los roles del autor. Los roles verificables con
     * la orden sola se evaluan primero, para no gastar una llamada a
     * catalog-service cuando la participación ya está confirmada.
     */
    private static final List<AuthorRole> ROLE_EVALUATION_ORDER = List.of(
            AuthorRole.CLIENTE,
            AuthorRole.REPARTIDOR,
            AuthorRole.RESTAURANTE);

    private final ReviewRepositoryPort reviewRepository;
    private final OrderLookupPort orderLookup;
    private final RestaurantLookupPort restaurantLookup;
    private final Clock clock;

    public CreateReviewUseCaseImpl(ReviewRepositoryPort reviewRepository,
                                    OrderLookupPort orderLookup,
                                    RestaurantLookupPort restaurantLookup,
                                    Clock clock) {
        this.reviewRepository = reviewRepository;
        this.orderLookup = orderLookup;
        this.restaurantLookup = restaurantLookup;
        this.clock = clock;
    }

    @Override
    public Review execute(CreateReviewCommand command) {
        if (command.orderId() == null) {
            throw new InvalidOrderIdException("El id de la orden es obligatorio.");
        }
        if (command.rating() == null) {
            throw new InvalidRatingException("La calificacion (rating) es obligatoria.");
        }
        Rating rating = Rating.of(command.rating());
        RatingTarget targetType = RatingTarget.fromValue(command.targetType());
        String comment = sanitizeComment(command.comment());
        Long authorUserId = command.authorUserId();

        OrderSnapshot order = orderLookup.findOrder(command.orderId());

        if (!order.isDelivered()) {
            throw new OrderNotDeliveredException(order.orderId(), order.status());
        }

        AuthorRole authorRole = resolveAuthorRole(order, targetType, authorUserId, command.authorRoles());

        Long targetId = order.targetIdFor(targetType);
        if (targetId == null) {
            throw new TargetNotFoundException(
                    "La orden " + order.orderId() + " no tiene un destino " + targetType.name() + " que calificar.");
        }

        if (targetType != RatingTarget.TIENDA && targetId.equals(authorUserId)) {
            throw new ReviewForbiddenException("No se puede calificar a uno mismo.");
        }

        if (reviewRepository.existsByOrderIdAndAuthorUserIdAndTargetType(
                order.orderId(), authorUserId, targetType)) {
            throw new ReviewAlreadyExistsException(order.orderId(), authorUserId, targetType.name());
        }

        Review review = Review.create(
                order.orderId(),
                authorUserId,
                targetType,
                targetId,
                rating,
                comment,
                OffsetDateTime.now(clock));

        Review saved = reviewRepository.save(review);
        log.info("review creada orderId={} targetType={} targetId={} authorUserId={} role={} rating={}",
                saved.getOrderId(), saved.getTargetType(), saved.getTargetId(),
                saved.getAuthorUserId(), authorRole, saved.getRating().value());
        return saved;
    }

    /**
     * Busca el rol del autor que habilita el destino <b>y</b> del que es
     * participante. El claim es multi-rol: se prueban todos los roles del autor
     * (nunca solo el primero).
     *
     * @throws ReviewForbiddenException si ningún rol del autor cumple ambas
     *         condiciones.
     */
    private AuthorRole resolveAuthorRole(OrderSnapshot order,
                                          RatingTarget targetType,
                                          Long authorUserId,
                                          Set<AuthorRole> authorRoles) {
        if (authorRoles == null || authorRoles.isEmpty()) {
            throw new ReviewForbiddenException(
                    "El token no tiene roles de autor de calificaciones.");
        }
        for (AuthorRole role : ROLE_EVALUATION_ORDER) {
            if (!authorRoles.contains(role)) {
                continue;
            }
            if (!RatingPolicy.allows(role, targetType)) {
                continue;
            }
            if (isParticipant(role, order, authorUserId)) {
                return role;
            }
        }
        throw new ReviewForbiddenException(
                "El usuario " + authorUserId + " no puede calificar un " + targetType.name()
                        + " en la orden " + order.orderId() + ": su rol no habilita esa relacion "
                        + "o no participa de la orden.");
    }

    /**
     * La participación se define por rol: el cliente es el {@code clientId} de
     * la orden, el repartidor es el {@code courierUserId} y el dueño de la
     * tienda es el propietario del {@code restaurantId}.
     */
    private boolean isParticipant(AuthorRole role, OrderSnapshot order, Long authorUserId) {
        return switch (role) {
            case CLIENTE -> order.isClient(authorUserId);
            case REPARTIDOR -> order.isCourier(authorUserId);
            case RESTAURANTE -> ownsRestaurant(order, authorUserId);
        };
    }

    private boolean ownsRestaurant(OrderSnapshot order, Long authorUserId) {
        if (authorUserId == null || order.restaurantId() == null) {
            return false;
        }
        return restaurantLookup.checkRestaurantMembership(authorUserId, order.restaurantId());
    }

    /**
     * Normaliza el comentario: descarta caracteres de control, recorta los
     * espacios de los extremos y lo deja en {@code null} si queda vacío.
     * El comentario se renderiza siempre como texto plano (S8).
     *
     * @throws InvalidCommentException si supera los 500 caracteres.
     */
    private String sanitizeComment(String rawComment) {
        if (rawComment == null) {
            return null;
        }
        if (rawComment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidCommentException(rawComment.length());
        }
        String sanitized = rawComment
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .trim();
        return sanitized.isEmpty() ? null : sanitized;
    }
}
