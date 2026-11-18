package com.flashdrop.reviews.domain.valueobjects;

import com.flashdrop.reviews.domain.exception.InvalidRatingException;

/**
 * Calificación en estrellas (S5): entero obligatorio de 1 a 5.
 *
 * <p>Value object inmutable: la invariantes de rango se aplican una sola vez,
 * en la fábrica {@link #of(int)}.</p>
 */
public final class Rating {

    /** Mínimo de la escala. */
    public static final int MIN = 1;

    /** Máximo de la escala. */
    public static final int MAX = 5;

    private final int value;

    private Rating(int value) {
        this.value = value;
    }

    /**
     * @param value cantidad de estrellas, entre {@link #MIN} y {@link #MAX} inclusive.
     * @return el value object con esa nota.
     * @throws InvalidRatingException si el valor está fuera del rango 1..5.
     */
    public static Rating of(int value) {
        if (value < MIN || value > MAX) {
            throw new InvalidRatingException(
                    "Rating invalido: " + value + ". El rango permitido es de " + MIN + " a " + MAX + " estrellas.");
        }
        return new Rating(value);
    }

    /**
     * @return la cantidad de estrellas (1..5).
     */
    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Rating rating)) {
            return false;
        }
        return value == rating.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
