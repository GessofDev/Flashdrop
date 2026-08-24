package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.RegisterUserCommand;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.PasswordHasher;
import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.RegisterUserService;
import com.flashdrop.auth.domain.exception.EmailAlreadyRegisteredException;
import com.flashdrop.auth.domain.exception.WeakPasswordException;
import com.flashdrop.auth.domain.model.Credentials;
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

/**
 * MIGRATION_PLAN.pdf §12.1 — tests unitarios de registro.
 *
 * <p>Puramente de dominio/aplicación: todos los puertos salientes están
 * mockeados, así que no toca Supabase ni la red.
 */
class RegisterUserServiceTest {

    private static final Role ROL_CLIENTE = new Role(1L, "Cliente", "/client/products/list");

    private final UserRepository users = mock(UserRepository.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(users, credentials, roles, hasher, audit);
        when(roles.findByName("Cliente")).thenReturn(Optional.of(ROL_CLIENTE));
    }

    @Test
    void registroValidoCreaUsuarioCredencialesYAsignaRolCliente() {
        var command = new RegisterUserCommand("Nuevo@FlashDrop.cl ", "Segura1234",
                "12.345.678-9", "Nuevo", "Cliente", "+56911112222");

        when(users.existsByEmail(any(Email.class))).thenReturn(false);
        when(hasher.hash("Segura1234")).thenReturn("hash-bcrypt");
        // El adaptador devuelve la fila ya persistida, con el id que generó Postgres.
        when(users.save(any(User.class))).thenReturn(new User(42L, new Email("nuevo@flashdrop.cl"),
                "12.345.678-9", "Nuevo", "Cliente", "+56911112222", null,
                List.of(ROL_CLIENTE), Instant.now()));

        var result = service.register(command);

        assertEquals(42L, result.userId());

        // El usuario se guarda normalizado, sin id (lo genera la BD) y con el rol base.
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        assertNull(savedUser.getValue().id());
        assertEquals("nuevo@flashdrop.cl", savedUser.getValue().email().value());
        assertEquals(List.of("Cliente"), savedUser.getValue().roleNames());

        // Las credenciales se guardan con el id que devolvió el repositorio,
        // el hash del hasher y nunca la contraseña en claro.
        ArgumentCaptor<Credentials> savedCredentials = ArgumentCaptor.forClass(Credentials.class);
        verify(credentials).save(savedCredentials.capture());
        assertEquals(42L, savedCredentials.getValue().userId());
        assertEquals("hash-bcrypt", savedCredentials.getValue().passwordHash());
        assertEquals("ACTIVE", savedCredentials.getValue().status());
    }

    @Test
    void emailDuplicadoLanzaEmailAlreadyRegistered() {
        var command = new RegisterUserCommand("cliente@demo.cl", "Segura1234",
                null, "Cliente", "Demo", null);
        when(users.existsByEmail(any(Email.class))).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> service.register(command));

        // No debe quedar ni usuario ni credencial a medio crear.
        verify(users, never()).save(any());
        verify(credentials, never()).save(any());
    }

    @Test
    void passwordDebilLanzaWeakPassword() {
        // "corta1" incumple el mínimo de 10 caracteres de PasswordPolicy.
        var command = new RegisterUserCommand("nuevo@flashdrop.cl", "corta1",
                null, "Nuevo", "Cliente", null);

        assertThrows(WeakPasswordException.class, () -> service.register(command));

        // La política se evalúa antes de tocar la base: ni siquiera se consulta
        // si el email existe.
        verify(users, never()).existsByEmail(any());
        verify(users, never()).save(any());
    }
}
