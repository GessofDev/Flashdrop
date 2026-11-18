package com.flashdrop.reviews.domain.model;

/**
 * Rol del autor de una calificación, tal como aparece en el claim {@code roles}
 * del JWT emitido por auth-service.
 *
 * <p>El claim es <b>multi-rol</b>: un mismo token puede traer más de un rol
 * (p. ej. {@code ["Cliente", "Restaurante"]}). Por eso este enum NO se resuelve
 * tomando "el primer rol" del claim, sino evaluando todos: la autorización
 * (matriz §4 del feature doc) busca <b>cualquier</b> rol del autor que a la vez
 * permita el tipo de destino y sea participante de la orden.</p>
 *
 * <p>Los valores del claim son exactamente los que emite auth-service:
 * {@code Cliente}, {@code Repartidor}, {@code Restaurante}.</p>
 */
public enum AuthorRole {

    CLIENTE("Cliente"),
    REPARTIDOR("Repartidor"),
    RESTAURANTE("Restaurante");

    private final String claimValue;

    AuthorRole(String claimValue) {
        this.claimValue = claimValue;
    }

    /**
     * @return el valor exacto del claim {@code roles} de auth-service.
     */
    public String claimValue() {
        return claimValue;
    }

    /**
     * Resuelve un valor del claim {@code roles} a su rol de dominio.
     *
     * <p>La comparación es <b>case-sensitive</b>: el claim de auth-service es un
     * vocabulario cerrado y exacto. Un valor desconocido (o {@code null}) se
     * rechaza con {@link IllegalArgumentException} — fail-closed, para que un
     * token con un rol que reviews-service no conoce termine en 403 y no en un
     * permiso implícito.</p>
     *
     * @throws IllegalArgumentException si el valor no corresponde a ningún rol conocido.
     */
    public static AuthorRole fromClaimValue(String value) {
        if (value != null) {
            for (AuthorRole role : values()) {
                if (role.claimValue.equals(value)) {
                    return role;
                }
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + value);
    }
}
