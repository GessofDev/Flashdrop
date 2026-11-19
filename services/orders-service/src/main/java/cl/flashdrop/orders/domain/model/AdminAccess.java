package cl.flashdrop.orders.domain.model;

import java.util.Set;

/**
 * Regla de administrador de FlashDrop. No existe un rol "Admin": es administrador quien tiene
 * los tres roles operativos a la vez (misma regla que {@code AdminAccess} de auth-service).
 *
 * <p>Esta clase es el unico lugar donde Orders decide quien es administrador. Si mas adelante se
 * crea un rol Admin real, se cambia aqui y no en cada endpoint.</p>
 */
public final class AdminAccess {

    public static final Set<Role> ADMIN_ROLES = Set.of(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR);

    private AdminAccess() {
    }

    public static boolean isAdmin(Set<Role> roles) {
        return roles != null && roles.containsAll(ADMIN_ROLES);
    }
}
