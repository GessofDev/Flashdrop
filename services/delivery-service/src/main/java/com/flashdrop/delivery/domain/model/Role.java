package com.flashdrop.delivery.domain.model;

import java.util.Arrays;

/**
 * Roles de usuario tal como los emite Auth en el claim {@code roles} del JWT.
 *
 * <p>Los valores de {@link #claimValue()} son los nombres reales de la tabla
 * {@code roles} de auth-service
 * ({@code services/auth-service/src/main/resources/db/seed/V2__seed_development.sql}):
 * {@code Cliente}, {@code Restaurante}, {@code Repartidor}. No existe un rol
 * "admin": {@code admin@demo.cl} es un usuario que tiene los tres roles a la
 * vez. Un usuario puede tener varios roles, por lo que los chequeos deben
 * verificar pertenencia al conjunto, nunca tomar "el primero".
 *
 * <p>Espejo de {@code orders-service/.../domain/model/Role.java} para que la
 * nomenclatura cross-service sea consistente.
 */
public enum Role {

    CLIENTE("Cliente"),
    RESTAURANTE("Restaurante"),
    REPARTIDOR("Repartidor");

    private final String claimValue;

    Role(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    /**
     * @param value el string que vino en el claim {@code roles} del JWT
     *              (e.g. {@code "Repartidor"}).
     * @return el enum correspondiente.
     * @throws IllegalArgumentException si el valor no matchea ningún rol conocido
     *         (incluye el caso {@code null} y el caso case-mismatch — los valores
     *         son case-sensitive y deben matchear exactamente lo que emite auth-service).
     */
    public static Role fromClaimValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Rol no reconocido: null");
        }
        return Arrays.stream(values())
                .filter(r -> r.claimValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol no reconocido: " + value));
    }
}
