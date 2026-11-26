package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.ConfirmEmailChangeCommand;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.ConfirmEmailChangeService;
import com.flashdrop.auth.domain.exception.EmailAlreadyInUseException;
import com.flashdrop.auth.domain.exception.InvalidEmailChangeTokenException;
import com.flashdrop.auth.domain.exception.InvalidEmailException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.exception.ResourceAlreadyExistsException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfirmEmailChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private final EmailChangeTokenStore emailChangeTokens = mock(EmailChangeTokenStore.class);
    private final OpaqueTokenService opaque = mock(OpaqueTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final EmailChangeMailer mailer = mock(EmailChangeMailer.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final TransactionalRunner txRunner = Runnable::run;

    private final ConfirmEmailChangeService service = new ConfirmEmailChangeService(
            emailChangeTokens, opaque, users, credentials, refreshTokens,
            mailer, rateLimiter, audit, txRunner, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User existingUser = new User(7L, new Email("old@demo.cl"), "11.111.111-1",
            "Nico", "Leiva", "+56911111111", null,
            List.of(new Role(1L, "Cliente", "/client")), NOW);

    @BeforeEach
    void setUp() {
        when(opaque.hash("raw-token")).thenReturn("hashed-token");
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(credentials.isActive(7L)).thenReturn(true);
    }

    @Test
    void confirmChange_validTokenAndNewEmail_updatesUsersAndLoginAndRevokesAllRefreshTokens() {
        when(users.existsByEmail(new Email("new@demo.cl"))).thenReturn(false);
        when(credentials.existsByLogin("new@demo.cl")).thenReturn(false);
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.of(7L));
        when(users.findById(7L)).thenReturn(Optional.of(existingUser));

        service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "  NEW@Demo.cl ", "1.2.3.4"));

        InOrder order = inOrder(emailChangeTokens, users, credentials, refreshTokens);
        order.verify(emailChangeTokens).consume("hashed-token", NOW);
        order.verify(users).updateEmail(7L, "new@demo.cl");
        order.verify(credentials).updateLogin(7L, "new@demo.cl");
        order.verify(refreshTokens).revokeAllForUser(7L);
        order.verify(emailChangeTokens).invalidateAllForUser(7L, NOW);

        verify(mailer).sendEmailChangedNotice("new@demo.cl", "Nico");
        verify(audit).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_CONFIRM", "7", "SUCCESS", "1.2.3.4"));
    }

    @Test
    void confirmChange_invalidEmail_doesNotConsumeToken() {
        assertThrows(InvalidEmailException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "not-an-email", "1.2.3.4")));

        verifyNoInteractions(emailChangeTokens, users, credentials, refreshTokens);
    }

    /**
     * El token se valida ANTES de mirar el email destino: si no, un llamador
     * sin sesion podria distinguir "ese email existe" (409) de "no existe"
     * (400) y enumerar las cuentas registradas.
     */
    @Test
    void confirmChange_invalidToken_neverRevealsWhetherTheEmailExists() {
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.empty());

        assertThrows(InvalidEmailChangeTokenException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "taken@demo.cl", "1.2.3.4")));

        verify(users, never()).existsByEmail(any());
        verify(credentials, never()).existsByLogin(any());
        verify(credentials, never()).updateLogin(any(), any());
    }

    @Test
    void confirmChange_emailCollision_throwsConflictWithoutChangingAnything() {
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.of(7L));
        when(users.existsByEmail(new Email("taken@demo.cl"))).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "taken@demo.cl", "1.2.3.4")));

        verify(users, never()).updateEmail(any(), any());
        verify(credentials, never()).updateLogin(any(), any());
        verifyNoInteractions(refreshTokens);
    }

    /** Una carrera de unicidad en la base llega como excepcion de dominio y se
     *  traduce al mismo contrato que una colision detectada antes. */
    @Test
    void confirmChange_uniqueRace_throwsTheSameConflictContract() {
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.of(7L));
        when(users.existsByEmail(new Email("new@demo.cl"))).thenReturn(false);
        when(credentials.existsByLogin("new@demo.cl")).thenReturn(false);
        when(users.findById(7L)).thenReturn(Optional.of(existingUser));
        doThrow(new ResourceAlreadyExistsException()).when(users).updateEmail(any(), any());

        assertThrows(EmailAlreadyInUseException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "new@demo.cl", "1.2.3.4")));

        verify(credentials, never()).updateLogin(any(), any());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void confirmChange_inactiveAccount_cannotChangeEmail() {
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.of(7L));
        when(credentials.isActive(7L)).thenReturn(false);

        assertThrows(InvalidEmailChangeTokenException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "new@demo.cl", "1.2.3.4")));

        verify(users, never()).updateEmail(any(), any());
        verify(credentials, never()).updateLogin(any(), any());
    }

    @Test
    void confirmChange_invalidOrExpiredToken_recordsRateLimitAndAuditFail() {
        when(emailChangeTokens.consume("hashed-token", NOW)).thenReturn(Optional.empty());

        assertThrows(InvalidEmailChangeTokenException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "new@demo.cl", "1.2.3.4")));

        verify(rateLimiter).recordFailure("email-change-confirm-ip:1.2.3.4");
        verify(audit).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_CONFIRM", null, "FAIL", "1.2.3.4"));
        verify(credentials, never()).updateLogin(any(), any());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void confirmChange_rateLimitExceeded_stopsBeforeValidatingEmailOrToken() {
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("email-change-confirm-ip:1.2.3.4");

        assertThrows(RateLimitExceededException.class,
                () -> service.confirmChange(new ConfirmEmailChangeCommand("raw-token", "new@demo.cl", "1.2.3.4")));

        verifyNoInteractions(emailChangeTokens, users, credentials, refreshTokens);
    }
}
