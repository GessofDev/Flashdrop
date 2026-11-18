package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body de {@code POST /api/reviews}.
 *
 * <p>El body <b>no</b> lleva el id del destino: lo deriva el servidor desde la
 * orden (S7). Solo se elige el <em>tipo</em> de destino.</p>
 *
 * @param orderId    id numérico público de la orden calificada.
 * @param targetType tipo de destino (TIENDA | REPARTIDOR | CLIENTE).
 * @param rating     nota de 1 a 5 estrellas.
 * @param comment    comentario opcional, hasta 500 caracteres.
 */
public record CreateReviewRequest(
        @NotNull(message = "orderId es obligatorio")
        Long orderId,

        @NotBlank(message = "targetType es obligatorio")
        String targetType,

        @NotNull(message = "rating es obligatorio")
        @Min(value = 1, message = "rating debe estar entre 1 y 5")
        @Max(value = 5, message = "rating debe estar entre 1 y 5")
        Integer rating,

        @Size(max = 500, message = "comment no puede superar los 500 caracteres")
        String comment
) {
}
