package com.flashdrop.auth.application;

import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.GetInternalUserService;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetInternalUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final GetInternalUserService service = new GetInternalUserService(users);

    @Test
    void usuarioExistenteRetornaSoloDatosDelContratoInterno() {
        when(users.findById(1L)).thenReturn(Optional.of(userWithRoles()));

        var response = service.getUser(1L);

        assertEquals(1L, response.id());
        assertEquals("Cliente", response.name());
        assertEquals("Demo", response.lastName());
        assertEquals("cliente@demo.cl", response.email());
        assertEquals("+56911111111", response.phone());
    }

    @Test
    void usuarioInexistenteLanzaUserNotFound() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrows(UserNotFoundException.class, () -> service.getUser(99L));

        assertEquals("User not found with id: 99", error.getMessage());
    }

    @Test
    void usuarioConRolesRetornaIdYNombre() {
        when(users.findById(1L)).thenReturn(Optional.of(userWithRoles()));

        var roles = service.getRoles(1L);

        assertEquals(2, roles.size());
        assertEquals(1L, roles.get(0).id());
        assertEquals("Cliente", roles.get(0).name());
        assertEquals(2L, roles.get(1).id());
        assertEquals("Restaurante", roles.get(1).name());
    }

    @Test
    void batchDeduplicaIgnoraNulosYNoPideLosInexistentes() {
        var cliente = new User(1L, new Email("cliente@demo.cl"), null, "Cliente", "Demo",
                "+56911111111", null, List.of(), Instant.now());
        // 999 no existe: el repositorio devuelve solo el que encontró.
        when(users.findAllByIds(List.of(1L, 999L))).thenReturn(List.of(cliente));

        var result = service.getUsers(Arrays.asList(1L, 1L, null, 999L));

        assertEquals(1, result.size());
        assertEquals("cliente@demo.cl", result.get(0).email());
    }

    @Test
    void batchSinIdsNoConsultaElRepositorio() {
        assertEquals(List.of(), service.getUsers(List.of()));
        assertEquals(List.of(), service.getUsers(null));
        verify(users, never()).findAllByIds(any());
    }

    @Test
    void usuarioSinRolesRetornaListaVacia() {
        var user = new User(
                2L,
                new Email("sinroles@demo.cl"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now());
        when(users.findById(2L)).thenReturn(Optional.of(user));

        assertEquals(List.of(), service.getRoles(2L));
    }

    private static User userWithRoles() {
        return new User(
                1L,
                new Email("cliente@demo.cl"),
                null,
                "Cliente",
                "Demo",
                "+56911111111",
                null,
                List.of(
                        new Role(1L, "Cliente", "/client"),
                        new Role(2L, "Restaurante", "/restaurant")),
                Instant.now());
    }
}
