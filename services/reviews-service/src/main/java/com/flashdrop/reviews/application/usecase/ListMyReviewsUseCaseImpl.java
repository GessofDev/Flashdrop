package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.dto.MyReviewsPage;
import com.flashdrop.reviews.application.port.inbound.ListMyReviewsUseCase;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.model.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementación de {@link ListMyReviewsUseCase}: el historial de lo que escribió
 * el autor.
 */
@Service
public class ListMyReviewsUseCaseImpl implements ListMyReviewsUseCase {

    private final ReviewRepositoryPort reviewRepository;

    public ListMyReviewsUseCaseImpl(ReviewRepositoryPort reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public MyReviewsPage execute(Long authorUserId, Pageable pageable) {
        List<Review> items = reviewRepository.findPublishedByAuthor(authorUserId, pageable);
        long total = reviewRepository.countPublishedByAuthor(authorUserId);

        return new MyReviewsPage(
                items,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                total,
                totalPages(total, pageable.getPageSize()));
    }

    private static int totalPages(long totalElements, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) pageSize);
    }
}
