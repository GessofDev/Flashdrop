package com.flashdrop.auth.domain;

import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Instant ALTA = Instant.parse("2026-01-15T10:00:00Z");

    private final User original = new User(7L, new Email("perfil@demo.cl"), "11.111.111-1",
            "Nombre", "Viejo", "+56911111111", null,
            List.of(new Role(3L, "Repartidor", "/delivery")), ALTA);

    @Test
    void conPerfilCambiaLosCuatroCamposEditables() {
        User editado = original.conPerfil("Nico", "Leiva", "+56999998888", "https://img/foto.png");

        assertEquals("Nico", editado.name());
        assertEquals("Leiva", editado.lastName());
        assertEquals("+56999998888", editado.phone());
        assertEquals("https://img/foto.png", editado.photo());
    }

    /** Si esto falla, el save escribe un email en null y el PUT tira
     *  InvalidUserException con un mensaje que no apunta al problema. */
    @Test
    void conPerfilConservaIdEmailRutRolesYFechaDeAlta() {
        User editado = original.conPerfil("Nico", "Leiva", null, null);

        assertEquals(7L, editado.id());
        assertEquals("perfil@demo.cl", editado.email().value());
        assertEquals("11.111.111-1", editado.rut());
        assertEquals(List.of("Repartidor"), editado.roleNames());
        assertEquals(ALTA, editado.createdAt());
    }

    @Test
    void conPerfilDevuelveOtraInstanciaYNoTocaLaOriginal() {
        User editado = original.conPerfil("Nico", "Leiva", null, null);

        assertNotSame(original, editado);
        assertEquals("Nombre", original.name());
        assertEquals("+56911111111", original.phone());
    }
}
