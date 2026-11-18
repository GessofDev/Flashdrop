package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.dto.PendingReviews;
import com.flashdrop.reviews.application.port.inbound.ListPendingReviewsUseCase;
import com.flashdrop.reviews.application.port.outbound.OrderLookupPort;
import com.flashdrop.reviews.application.port.outbound.RestaurantLookupPort;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.model.OrderSnapshot;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.policy.RatingPolicy;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementación de {@link ListPendingReviewsUseCase}.
 *
 * <p>Los destinos pendientes son los que la matriz §4 habilita para los roles
 * del autor <b>en los que participa de esa orden</b>, menos los que ya
 * calificó. Una orden que todavía no está entregada no habilita nada: la
 * lista de pendientes viene vacía (las ya escritas se devuelven igual).</p>
 */
@Service
public class ListPendingReviewsUseCaseImpl implements ListPendingReviewsUseCase {

    private static final List<AuthorRole> ROLE_EVALUATION_ORDER = List.of(
            AuthorRole.CLIENTE,
            AuthorRole.REPARTIDOR,
            AuthorRole.RESTAURANTE);

    private final ReviewRepositoryPort reviewRepository;
    private final OrderLookupPort orderLookup;
    private final RestaurantLookupPort restaurantLookup;

    public ListPendingReviewsUseCaseImpl(ReviewRepositoryPort reviewRepository,
                                          OrderLookupPort orderLookup,
                                          RestaurantLookupPort restaurantLookup) {
        this.reviewRepository = reviewRepository;
        this.orderLookup = orderLookup;
        this.restaurantLookup = restaurantLookup;
    }

    @Override
    public PendingReviews execute(Long orderId, Long authorUserId, Set<AuthorRole> authorRoles) {
        OrderSnapshot order = orderLookup.findOrder(orderId);
        List<Review> completed = reviewRepository.findByOrderIdAndAuthorUserId(orderId, authorUserId);

        if (!order.isDelivered()) {
            return new PendingReviews(orderId, List.of(), completed);
        }

        Set<RatingTarget> eligible = eligibleTargets(order, authorUserId, authorRoles);

        Set<RatingTarget> alreadyDone = new LinkedHashSet<>();
        for (Review review : completed) {
            alreadyDone.add(review.getTargetType());
        }

        List<PendingReviews.PendingTarget> pending = new ArrayList<>();
        for (RatingTarget target : eligible) {
            if (alreadyDone.contains(target)) {
                continue;
            }
            Long targetId = order.targetIdFor(target);
            if (targetId == null) {
                // La orden no tiene ese participante: no hay nada que calificar.
                continue;
            }
            pending.add(new PendingReviews.PendingTarget(target, targetId));
        }

        return new PendingReviews(orderId, List.copyOf(pending), completed);
    }

    /**
     * Une los destinos habilitados por la matriz para cada rol del autor en el
     * que participa de la orden.
     */
    private Set<RatingTarget> eligibleTargets(OrderSnapshot order, Long authorUserId, Set<AuthorRole> authorRoles) {
        Set<RatingTarget> eligible = EnumSet.noneOf(RatingTarget.class);
        if (authorRoles == null || authorRoles.isEmpty()) {
            return eligible;
        }
        for (AuthorRole role : ROLE_EVALUATION_ORDER) {
            if (!authorRoles.contains(role) || !isParticipant(role, order, authorUserId)) {
                continue;
            }
            eligible.addAll(RatingPolicy.allowedTargets(role));
        }
        return eligible;
    }

    private boolean isParticipant(AuthorRole role, OrderSnapshot order, Long authorUserId) {
        return switch (role) {
            case CLIENTE -> order.isClient(authorUserId);
            case REPARTIDOR -> order.isCourier(authorUserId);
            case RESTAURANTE -> restaurantLookup.findRestaurantIdByOwner(authorUserId)
                    .map(restaurantId -> restaurantId.equals(order.restaurantId()))
                    .orElse(false);
        };
    }
}
