package com.flashdrop.reviews.application.port.inbound;

import com.flashdrop.reviews.application.dto.TargetReviewsPage;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.springframework.data.domain.Pageable;

/**
 * Caso de uso: listar las calificaciones públicas de un destino, con su
 * agregado (promedio, cantidad y distribución) (S8).
 */
public interface ListReviewsForTargetUseCase {

    /**
     * @param targetType tipo de destino.
     * @param targetId   id del destino.
     * @param pageable   paginación.
     * @return la página de calificaciones con el agregado del destino.
     */
    TargetReviewsPage execute(RatingTarget targetType, Long targetId, Pageable pageable);
}
