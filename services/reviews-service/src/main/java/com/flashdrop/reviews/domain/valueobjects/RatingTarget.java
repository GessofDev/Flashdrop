package com.flashdrop.reviews.domain.valueobjects;

import com.flashdrop.reviews.domain.exception.InvalidTargetTypeException;

/**
 * Tipo de destino de una calificación (S4, spec §4).
 *
 * <p>Tres destinos posibles, y el destino es siempre <b>derivado por el
 * servidor</b> desde la orden (S7): el cliente nunca envía el id del actor
 * que califica.</p>
 *
 * <ul>
 *   <li>{@link #TIENDA} → {@code catalog.restaurant.id}</li>
 *   <li>{@link #REPARTIDOR} → {@code auth.users.id} del repartidor</li>
 *   <li>{@link #CLIENTE} → {@code auth.users.id} del cliente</li>
 * </ul>
 *
 * <p>El nombre del enum es el valor persistido ({@code VARCHAR(16)} en
 * {@code internal.reviews.target_type}).</p>
 */
public enum RatingTarget {

    TIENDA,
    REPARTIDOR,
    CLIENTE;

    /**
     * Parsea el tipo de destino desde su representación de API.
     *
     * <p>El parseo es <b>estricto</b>: solo se acepta el nombre exacto del enum.
     * Se rechazan diferencias de mayúsculas/minusúsculas, espacios sobrantes y
     * cualquier valor desconocido, porque el tipo de destino decide a qué
     * espacio de ids apunta el id derivado y un parseo laxo abriría la puerta a
     * apuntar al id equivocado.</p>
     *
     * @throws InvalidTargetTypeException si el valor no es uno de los tres tipos válidos.
     */
    public static RatingTarget fromValue(String value) {
        if (value != null) {
            for (RatingTarget target : values()) {
                if (target.name().equals(value)) {
                    return target;
                }
            }
        }
        throw new InvalidTargetTypeException(value);
    }
}
