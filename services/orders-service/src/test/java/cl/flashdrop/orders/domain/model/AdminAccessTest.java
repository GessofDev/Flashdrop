package cl.flashdrop.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAccessTest {

    @Test
    void usuarioConLosTresRolesOperativosEsAdministrador() {
        assertTrue(AdminAccess.isAdmin(EnumSet.of(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR)));
    }

    @Test
    void soporteNoVuelveAdministradorANadie() {
        assertTrue(AdminAccess.isAdmin(EnumSet.allOf(Role.class)));
        assertFalse(AdminAccess.isAdmin(EnumSet.of(Role.SOPORTE, Role.CLIENTE)));
    }

    @Test
    void conMenosDeTresRolesNoEsAdministrador() {
        assertFalse(AdminAccess.isAdmin(EnumSet.of(Role.CLIENTE, Role.RESTAURANTE)));
        assertFalse(AdminAccess.isAdmin(Set.of()));
        assertFalse(AdminAccess.isAdmin(null));
    }
}
