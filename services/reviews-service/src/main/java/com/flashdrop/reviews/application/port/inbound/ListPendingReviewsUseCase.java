package com.flashdrop.reviews.application.port.inbound;

import com.flashdrop.reviews.application.dto.PendingReviews;
import com.flashdrop.reviews.domain.model.AuthorRole;

import java.util.Set;

/**
 * Caso de uso: listar las calificaciones pendientes del autor en una orden y
 * las que ya hizo (alimenta el formulario post-entrega).
 */
public interface ListPendingReviewsUseCase {

    /**
     * @param orderId      id numérico público de la orden.
     * @param authorUserId autor autenticado.
     * @param authorRoles  roles del autor (claim multi-rol).
     * @return destinos pendientes y calificaciones ya escritas por el autor.
     */
    PendingReviews execute(Long orderId, Long authorUserId, Set<AuthorRole> authorRoles);
}
