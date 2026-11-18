package com.flashdrop.delivery.domain.model;

/**
 * Tipo de destino de una calificacion. Es el discriminante del espacio de ids
 * que usa reviews-service: {@code REPARTIDOR} y {@code CLIENTE} son
 * auth.users.id.
 */
public enum RatingTargetType {

    TIENDA,
    REPARTIDOR,
    CLIENTE
}
