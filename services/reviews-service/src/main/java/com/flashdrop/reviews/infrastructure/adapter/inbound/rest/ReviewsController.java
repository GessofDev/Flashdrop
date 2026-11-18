package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.dto.CreateReviewCommand;
import com.flashdrop.reviews.application.dto.PendingReviews;
import com.flashdrop.reviews.application.dto.TargetReviewsPage;
import com.flashdrop.reviews.application.port.inbound.CreateReviewUseCase;
import com.flashdrop.reviews.application.port.inbound.ListPendingReviewsUseCase;
import com.flashdrop.reviews.application.port.inbound.ListReviewsForTargetUseCase;
import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.CreateReviewRequest;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.PendingReviewsResponse;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.ReviewResponse;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.TargetReviewsResponse;
import com.flashdrop.reviews.infrastructure.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API pública de calificaciones (T8).
 *
 * <ul>
 *   <li>{@code POST /api/reviews} — crea la calificación (201).</li>
 *   <li>{@code GET /api/reviews/pending/{orderId}} — pendientes + ya hechas.</li>
 *   <li>{@code GET /api/reviews/{targetType}/{targetId}} — lista paginada + agregado.</li>
 * </ul>
 *
 * <p>La identidad del autor sale del JWT (subject = user id, claim
 * {@code roles}), nunca del body.</p>
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewsController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CreateReviewUseCase createReview;
    private final ListPendingReviewsUseCase listPendingReviews;
    private final ListReviewsForTargetUseCase listReviewsForTarget;
    private final CurrentUserResolver currentUserResolver;

    public ReviewsController(CreateReviewUseCase createReview,
                             ListPendingReviewsUseCase listPendingReviews,
                             ListReviewsForTargetUseCase listReviewsForTarget,
                             CurrentUserResolver currentUserResolver) {
        this.createReview = createReview;
        this.listPendingReviews = listPendingReviews;
        this.listReviewsForTarget = listReviewsForTarget;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Crea una calificación.
     *
     * <p>Respuestas: 201 creada · 400 payload inválido · 403 rol o relación no
     * habilitan la calificación · 404 la orden o el destino no existen · 409 ya
     * se calificó · 422 la orden no está entregada.</p>
     */
    @PostMapping
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        Long authorUserId = currentUserResolver.requireCurrentUserId();
        Set<AuthorRole> authorRoles = currentUserResolver.requireCurrentRoles();

        Review review = createReview.execute(new CreateReviewCommand(
                request.orderId(),
                request.targetType(),
                request.rating(),
                request.comment(),
                authorUserId,
                authorRoles));

        ReviewResponse body = ReviewResponse.from(review);
        return ResponseEntity
                .created(URI.create("/api/reviews/" + body.id()))
                .body(body);
    }

    /**
     * Qué calificaciones puede hacer todavía el autor en esa orden y cuáles ya
     * hizo.
     */
    @GetMapping("/pending/{orderId}")
    public PendingReviewsResponse pending(@PathVariable Long orderId) {
        Long authorUserId = currentUserResolver.requireCurrentUserId();
        Set<AuthorRole> authorRoles = currentUserResolver.requireCurrentRoles();

        PendingReviews result = listPendingReviews.execute(orderId, authorUserId, authorRoles);

        List<PendingReviewsResponse.PendingTargetResponse> pending = result.pending().stream()
                .map(target -> new PendingReviewsResponse.PendingTargetResponse(
                        target.targetType().name(), target.targetId()))
                .toList();

        return new PendingReviewsResponse(
                result.orderId(),
                pending,
                result.completed().stream().map(ReviewResponse::from).toList());
    }

    /**
     * Lista paginada de las calificaciones públicas de un destino, con su
     * agregado (promedio, cantidad y distribución).
     */
    @GetMapping("/{targetType}/{targetId}")
    public TargetReviewsResponse listForTarget(@PathVariable String targetType,
                                                @PathVariable Long targetId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        RatingTarget target = RatingTarget.fromValue(targetType);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));

        TargetReviewsPage result = listReviewsForTarget.execute(target, targetId, pageable);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            distribution.put(String.valueOf(rating), result.distribution().getOrDefault(rating, 0L));
        }

        return new TargetReviewsResponse(
                result.items().stream().map(ReviewResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                new TargetReviewsResponse.RatingAggregateResponse(
                        result.average(), result.count(), distribution));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
