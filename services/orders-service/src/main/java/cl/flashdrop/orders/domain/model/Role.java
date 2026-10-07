package cl.flashdrop.orders.domain.model;

import lombok.Getter;

/**
 * Roles de usuario tal como los emite Auth en el claim {@code roles} del JWT.
 *
 * <p>Los valores son los nombres reales de la tabla {@code roles} de auth-service
 * ({@code V2__seed_development.sql}): {@code Cliente}, {@code Restaurante},
 * {@code Repartidor}. No existe un rol "admin": {@code admin@demo.cl} es un usuario
 * que tiene los tres roles a la vez. Un usuario puede tener varios roles, por lo que
 * los chequeos deben verificar pertenencia al conjunto, nunca tomar "el primero".</p>
 */
@Getter
public enum Role {

    CLIENTE("Cliente"),
    RESTAURANTE("Restaurante"),
    REPARTIDOR("Repartidor");

    private final String claimValue;

    Role(String claimValue) {
        this.claimValue = claimValue;
    }

    public static Role fromClaimValue(String value) {
        for (Role role : values()) {
            if (role.claimValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol no reconocido: " + value);
    }
}
