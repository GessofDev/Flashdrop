package com.flashdrop.reviews.application.dto;

import com.flashdrop.reviews.domain.model.AuthorRole;

import java.util.Set;

/**
 * Comando de creación de una calificación.
 *
 * <p>El destino ({@code targetType}) lo elige el autor, pero su <b>id</b> no:
 * el id del destino lo deriva el servidor desde la orden (S7). Por eso el
 * comando no lleva ningún id de destino.</p>
 *
 * @param orderId      id numérico público de la orden calificada.
 * @param targetType   tipo de destino (TIENDA | REPARTIDOR | CLIENTE).
 * @param rating       nota de 1 a 5 estrellas.
 * @param comment      comentario opcional (máx. 500 caracteres).
 * @param authorUserId autor, tomado del {@code sub} del JWT.
 * @param authorRoles  roles del autor, tomados del claim {@code roles} (multi-rol).
 */
public record CreateReviewCommand(
        Long orderId,
        String targetType,
        Integer rating,
        String comment,
        Long authorUserId,
        Set<AuthorRole> authorRoles
) {
}
