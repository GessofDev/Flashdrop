package com.flashdrop.reviews.domain.model;

/**
 * Estado de publicación de una calificación.
 *
 * <p>Existe para poder moderar sin borrar (T17, fuera de v1). En v1 todas las
 * calificaciones nacen {@link #PUBLICADO}.</p>
 */
public enum ReviewStatus {

    PUBLICADO,
    OCULTO;

    /**
     * @return {@code true} si la calificación es visible públicamente (S8).
     */
    public boolean isPublic() {
        return this == PUBLICADO;
    }
}
