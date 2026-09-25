package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.UpdateUserProfileCommand;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.UpdateUserProfileService;
import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserProfileServiceTest {

    private static final Instant ALTA = Instant.parse("2026-01-15T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final UpdateUserProfileService service = new UpdateUserProfileService(users);

    @BeforeEach
    void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(new User(
                1L, new Email("cliente@demo.cl"), "11.111.111-1", "Cliente", "Demo",
                "+56911111111", "https://img/vieja.png",
                List.of(new Role(1L, "Cliente", "/client")), ALTA)));
        // El adaptador real devuelve lo que guardo; aca se imita eso.
        when(users.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void editaLosCamposYDevuelveElPerfilActualizado() {
        var perfil = service.updateProfile(1L, new UpdateUserProfileCommand(
                "Nico", "Leiva", "+56999998888", "https://img/nueva.png"));

        assertEquals(1L, perfil.userId());
        assertEquals("Nico", perfil.name());
        assertEquals("Leiva", perfil.lastName());
        assertEquals("+56999998888", perfil.phone());
        assertEquals("https://img/nueva.png", perfil.photo());
        assertEquals(List.of("Cliente"), perfil.roles());
    }

    /** Lo que llega al repositorio es el usuario completo, no solo lo editado:
     *  el save reescribe todas las columnas. */
    @Test
    void guardaConservandoEmailRutRolesYFechaDeAlta() {
        service.updateProfile(1L, new UpdateUserProfileCommand("Nico", "Leiva", null, null));

        User guardado = guardado();
        assertEquals("cliente@demo.cl", guardado.email().value());
        assertEquals("11.111.111-1", guardado.rut());
        assertEquals(List.of("Cliente"), guardado.roleNames());
        assertEquals(ALTA, guardado.createdAt());
    }

    /** Misma regla que el alta. Sin esto el mismo numero podria quedar con y
     *  sin espacios, y la restriccion unica no lo veria repetido. */
    @Test
    void elTelefonoSeNormalizaIgualQueEnElAlta() {
        service.updateProfile(1L, new UpdateUserProfileCommand("Nico", "Leiva", "+56 9 8888 7777", null));

        assertEquals("+56988887777", guardado().phone());
    }

    @Test
    void telefonoInvalidoSeRechazaSinGuardar() {
        assertThrows(InvalidUserException.class, () -> service.updateProfile(1L,
                new UpdateUserProfileCommand("Nico", "Leiva", "123", null)));

        verify(users, never()).save(any());
    }

    /** Reemplazo completo: telefono y foto en null los quitan. */
    @Test
    void telefonoYFotoEnNullQuedanVacios() {
        service.updateProfile(1L, new UpdateUserProfileCommand("Nico", "Leiva", null, null));

        User guardado = guardado();
        assertNull(guardado.phone());
        assertNull(guardado.photo());
    }

    @Test
    void usuarioInexistenteLanzaUserNotFoundSinGuardar() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.updateProfile(99L,
                new UpdateUserProfileCommand("Nico", "Leiva", null, null)));

        verify(users, never()).save(any());
    }

    private User guardado() {
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }
}
