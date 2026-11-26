package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.RequestEmailChangeCommand;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.RequestEmailChangeService;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.EmailChangeToken;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequestEmailChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String LINK_BASE = "https://app.flashdrop.cl/email-change";

    private final UserRepository users = mock(UserRepository.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final EmailChangeTokenStore emailChangeTokens = mock(EmailChangeTokenStore.class);
    private final OpaqueTokenService opaque = mock(OpaqueTokenService.class);
    private final EmailChangeMailer mailer = mock(EmailChangeMailer.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private final RequestEmailChangeService service = new RequestEmailChangeService(
            users, credentials, hasher, emailChangeTokens, opaque, mailer,
            rateLimiter, audit, Clock.fixed(NOW, ZoneOffset.UTC), TTL, LINK_BASE);

    private final User user = new User(7L, new Email("actual@demo.cl"), "11.111.111-1",
            "Nico", "Leiva", "+56911111111", null,
            List.of(new Role(1L, "Cliente", "/client")), NOW);

    private final Credentials activeCreds = new Credentials(10L, 7L, "actual@demo.cl", "bcrypt-hash", "ACTIVE");

    @BeforeEach
    void setUp() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(credentials.findByUserId(7L)).thenReturn(Optional.of(activeCreds));
        when(opaque.generate()).thenReturn("raw-token");
        when(opaque.hash("raw-token")).thenReturn("hashed-token");
    }

    @Test
    void requestChange_validPassword_invalidatesOldTokensSavesHashAndEmailsCurrentAddress() {
        when(hasher.matches("Secret123", "bcrypt-hash")).thenReturn(true);

        service.requestChange(new RequestEmailChangeCommand(7L, "Secret123", "1.2.3.4"));

        verify(emailChangeTokens).invalidateAllForUser(7L, NOW);

        ArgumentCaptor<EmailChangeToken> tokenCaptor = ArgumentCaptor.forClass(EmailChangeToken.class);
        verify(emailChangeTokens).save(tokenCaptor.capture());
        assertEquals(7L, tokenCaptor.getValue().userId());
        assertEquals("hashed-token", tokenCaptor.getValue().tokenHash());
        assertEquals(NOW.plus(TTL), tokenCaptor.getValue().expiresAt());

        verify(mailer).sendConfirmationLink("actual@demo.cl", "Nico", LINK_BASE + "#token=raw-token", TTL);
        verify(audit).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_REQUEST", "7", "SUCCESS", "1.2.3.4"));
    }

    /** Cada solicitud puede mandar un correo: una peticion valida tambien
     *  cuenta contra el limite, si no el endpoint es una primitiva de inundacion. */
    @Test
    void requestChange_validPassword_countsAgainstTheRateLimit() {
        when(hasher.matches("Secret123", "bcrypt-hash")).thenReturn(true);

        service.requestChange(new RequestEmailChangeCommand(7L, "Secret123", "1.2.3.4"));

        verify(rateLimiter).recordFailure("email-change-req:7");
        verify(rateLimiter).recordFailure("email-change-ip:1.2.3.4");
        verify(rateLimiter, never()).reset(any());
    }

    @Test
    void requestChange_wrongPassword_throwsInvalidCredentialsAndDoesNotSendEmail() {
        when(hasher.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.requestChange(new RequestEmailChangeCommand(7L, "wrong", "1.2.3.4")));

        verify(audit).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_REQUEST", "7", "FAIL", "1.2.3.4"));
        verifyNoInteractions(emailChangeTokens, mailer);
    }

    @Test
    void requestChange_rateLimitExceeded_stopsBeforeCheckingPassword() {
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("email-change-req:7");

        assertThrows(RateLimitExceededException.class,
                () -> service.requestChange(new RequestEmailChangeCommand(7L, "Secret123", "1.2.3.4")));

        verifyNoInteractions(users, credentials, emailChangeTokens, mailer);
    }

    @Test
    void requestChange_mailFailure_recordsAuditFailInvalidatesTokenAndRethrows() {
        when(hasher.matches("Secret123", "bcrypt-hash")).thenReturn(true);
        doThrow(new IllegalStateException("smtp caido")).when(mailer)
                .sendConfirmationLink(any(), any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> service.requestChange(new RequestEmailChangeCommand(7L, "Secret123", "1.2.3.4")));

        // El enlace nunca salio: el token pendiente no debe quedar vivo.
        verify(emailChangeTokens, times(2)).invalidateAllForUser(eq(7L), any());
        verify(audit).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_REQUEST", "7", "FAIL", "1.2.3.4"));
        verify(audit, never()).record(new AuditLogger.AuditEvent("EMAIL_CHANGE_REQUEST", "7", "SUCCESS", "1.2.3.4"));
    }
}
