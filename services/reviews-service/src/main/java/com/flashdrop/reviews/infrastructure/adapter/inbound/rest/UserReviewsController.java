package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.dto.MyReviewsPage;
import com.flashdrop.reviews.application.port.inbound.ListMyReviewsUseCase;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.MyReviewsResponse;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.ReviewResponse;
import com.flashdrop.reviews.infrastructure.security.CurrentUserResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de las calificaciones del usuario autenticado (T8).
 *
 * <p>{@code GET /api/users/me/reviews} — devuelve solo lo que escribió el
 * autor de la llamada; el id del usuario sale del JWT.</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserReviewsController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ListMyReviewsUseCase listMyReviews;
    private final CurrentUserResolver currentUserResolver;

    public UserReviewsController(ListMyReviewsUseCase listMyReviews,
                                 CurrentUserResolver currentUserResolver) {
        this.listMyReviews = listMyReviews;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/me/reviews")
    public MyReviewsResponse myReviews(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Long authorUserId = currentUserResolver.requireCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));

        MyReviewsPage result = listMyReviews.execute(authorUserId, pageable);

        return new MyReviewsResponse(
                result.items().stream().map(ReviewResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
