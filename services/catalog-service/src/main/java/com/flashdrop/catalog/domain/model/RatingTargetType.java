package com.flashdrop.catalog.domain.model;

/**
 * Tipo de destino de una calificacion. Es el discriminante del espacio de ids
 * que usa reviews-service: {@code TIENDA} es catalog.restaurant.id y
 * {@code REPARTIDOR} / {@code CLIENTE} son auth.users.id.
 */
public enum RatingTargetType {

    TIENDA,
    REPARTIDOR,
    CLIENTE
}
