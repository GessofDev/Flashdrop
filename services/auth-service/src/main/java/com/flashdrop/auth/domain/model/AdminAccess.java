package com.flashdrop.auth.domain.model;

import java.util.List;
import java.util.Set;

/**
 * En FlashDrop no existe un rol "Admin": el administrador es el usuario que tiene los tres roles
 * operativos a la vez (como admin@demo.cl). Esta clase concentra esa regla.
 */
public final class AdminAccess {

    public static final Set<String> ADMIN_ROLES = Set.of("Cliente", "Restaurante", "Repartidor");

    private AdminAccess() {
    }

    public static boolean isAdmin(List<String> roleNames) {
        return roleNames != null && roleNames.containsAll(ADMIN_ROLES);
    }
}
