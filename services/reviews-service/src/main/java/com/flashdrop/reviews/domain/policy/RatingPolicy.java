package com.flashdrop.reviews.domain.policy;

import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Matriz de autorización de la spec §4 (S4): las 6 relaciones permitidas entre
 * autor y destino.
 *
 * <pre>
 * Autor \ Destino | TIENDA | REPARTIDOR | CLIENTE
 * CLIENTE         |   sí   |     sí     |   no
 * REPARTIDOR      |   sí   |     no     |   sí
 * RESTAURANTE     |   no   |     sí     |   sí
 * </pre>
 *
 * <p>La matriz es <b>solo una mitad</b> de la autorización: el autor además
 * tiene que ser participante de la orden (ver el caso de uso
 * {@code CreateReview}). Esta clase no conoce órdenes ni usuarios — es política
 * pura de rol × destino.</p>
 *
 * <p>La ausencia de auto-calificación (CLIENTE→CLIENTE y REPARTIDOR→REPARTIDOR)
 * está implícita en la matriz y además se verifica de forma explícita contra el
 * id derivado en el caso de uso.</p>
 */
public final class RatingPolicy {

    private static final Set<RatingTarget> CLIENTE_TARGETS = Collections.unmodifiableSet(
            EnumSet.of(RatingTarget.TIENDA, RatingTarget.REPARTIDOR));

    private static final Set<RatingTarget> REPARTIDOR_TARGETS = Collections.unmodifiableSet(
            EnumSet.of(RatingTarget.TIENDA, RatingTarget.CLIENTE));

    private static final Set<RatingTarget> RESTAURANTE_TARGETS = Collections.unmodifiableSet(
            EnumSet.of(RatingTarget.REPARTIDOR, RatingTarget.CLIENTE));

    private RatingPolicy() {
        // utility class
    }

    /**
     * @param role   rol del autor (del claim {@code roles}).
     * @param target tipo de destino.
     * @return {@code true} si la relación rol → destino está permitida por la
     *         matriz §4.
     */
    public static boolean allows(AuthorRole role, RatingTarget target) {
        if (role == null || target == null) {
            return false;
        }
        return allowedTargets(role).contains(target);
    }

    /**
     * @param role rol del autor.
     * @return los tipos de destino permitidos para ese rol (conjunto inmutable).
     */
    public static Set<RatingTarget> allowedTargets(AuthorRole role) {
        if (role == null) {
            return Set.of();
        }
        return switch (role) {
            case CLIENTE -> CLIENTE_TARGETS;
            case REPARTIDOR -> REPARTIDOR_TARGETS;
            case RESTAURANTE -> RESTAURANTE_TARGETS;
        };
    }
}
