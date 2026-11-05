package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.ResetPasswordCommand;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.ResetPasswordService;
import com.flashdrop.auth.domain.exception.InvalidResetTokenException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResetPasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final String CLAVE = "NuevaClave123";

    private final PasswordResetTokenStore resetTokens = mock(PasswordResetTokenStore.class);
    private final OpaqueTokenService opaque = mock(OpaqueTokenService.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private final ResetPasswordService service = new ResetPasswordService(resetTokens, opaque, credentials,
            refreshTokens, hasher, rateLimiter, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(opaque.hash("crudo")).thenReturn("hash-crudo");
        when(hasher.hash(CLAVE)).thenReturn("bcrypt");
    }

    @Test
    void tokenValidoCambiaLaClaveYCierraLasSesiones() {
        when(resetTokens.consume("hash-crudo", NOW)).thenReturn(Optional.of(7L));

        service.reset(new ResetPasswordCommand("crudo", CLAVE, "1.2.3.4"));

        InOrder orden = inOrder(resetTokens, credentials, refreshTokens);
        orden.verify(resetTokens).consume("hash-crudo", NOW);
        orden.verify(credentials).updatePassword(7L, "bcrypt");
        orden.verify(refreshTokens).revokeAllForUser(7L);
        orden.verify(resetTokens).invalidateAllForUser(7L, NOW);
    }

    @Test
    void tokenInvalidoOVencidoFallaYCuentaParaElLimite() {
        when(resetTokens.consume("hash-crudo", NOW)).thenReturn(Optional.empty());

        assertThrows(InvalidResetTokenException.class,
                () -> service.reset(new ResetPasswordCommand("crudo", CLAVE, "1.2.3.4")));

        verify(rateLimiter).recordFailure("reset-ip:1.2.3.4");
        verifyNoInteractions(credentials, refreshTokens);
    }

    @Test
    void claveDebilNoGastaElEnlace() {
        assertThrows(WeakPasswordException.class,
                () -> service.reset(new ResetPasswordCommand("crudo", "corta", "1.2.3.4")));

        verify(resetTokens, never()).consume(any(), any());
    }

    @Test
    void elLimitePorIpCortaAntesDeTocarElToken() {
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("reset-ip:1.2.3.4");

        assertThrows(RateLimitExceededException.class,
                () -> service.reset(new ResetPasswordCommand("crudo", CLAVE, "1.2.3.4")));

        verifyNoInteractions(resetTokens);
    }
}
