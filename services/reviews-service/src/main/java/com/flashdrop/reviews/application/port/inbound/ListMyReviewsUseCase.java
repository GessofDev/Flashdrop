package com.flashdrop.reviews.application.port.inbound;

import com.flashdrop.reviews.application.dto.MyReviewsPage;
import org.springframework.data.domain.Pageable;

/**
 * Caso de uso: historial de las calificaciones escritas por el autor.
 */
public interface ListMyReviewsUseCase {

    /**
     * @param authorUserId autor autenticado.
     * @param pageable     paginación.
     * @return la página de calificaciones del autor.
     */
    MyReviewsPage execute(Long authorUserId, Pageable pageable);
}
