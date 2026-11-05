package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.RequestPasswordResetCommand;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.RequestPasswordResetService;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.PasswordResetToken;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequestPasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String BASE = "https://web.test/recuperar-clave";

    private final CredentialStore credentials = mock(CredentialStore.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenStore resetTokens = mock(PasswordResetTokenStore.class);
    private final OpaqueTokenService opaque = mock(OpaqueTokenService.class);
    private final PasswordResetMailer mailer = mock(PasswordResetMailer.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private final RequestPasswordResetService service = service(Runnable::run);

    private RequestPasswordResetService service(Executor executor) {
        return new RequestPasswordResetService(credentials, users, resetTokens, opaque, mailer, rateLimiter,
                audit, executor, Clock.fixed(NOW, ZoneOffset.UTC), TTL, BASE);
    }

    private void usuarioActivo() {
        when(credentials.findByLogin("ana@x.cl"))
                .thenReturn(Optional.of(new Credentials(1L, 7L, "ana@x.cl", "hash", "ACTIVE")));
        when(users.findById(7L)).thenReturn(Optional.of(new User(
                7L, new Email("ana@x.cl"), null, "Ana", "P", null, null, List.of(), NOW)));
        when(opaque.generate()).thenReturn("crudo");
        when(opaque.hash("crudo")).thenReturn("hash-crudo");
    }

    @Test
    void correoExistenteGuardaSoloElHashYEnviaElEnlaceConElTokenCrudo() {
        usuarioActivo();

        service.requestReset(new RequestPasswordResetCommand(" Ana@X.cl ", "1.2.3.4"));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        assertEquals(7L, saved.getValue().userId());
        assertEquals("hash-crudo", saved.getValue().tokenHash());
        assertEquals(NOW.plus(TTL), saved.getValue().expiresAt());
        verify(mailer).send("ana@x.cl", "Ana", BASE + "#token=crudo", TTL);
    }

    @Test
    void unaSolicitudNuevaInvalidaLosEnlacesAnteriores() {
        usuarioActivo();

        service.requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4"));

        verify(resetTokens).invalidateAllForUser(7L, NOW);
    }

    @Test
    void correoInexistenteNoFallaNiEnvia() {
        when(credentials.findByLogin("nadie@x.cl")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset(new RequestPasswordResetCommand("nadie@x.cl", "1.2.3.4")));

        verifyNoInteractions(mailer, resetTokens);
    }

    @Test
    void cuentaInactivaNoRecibeEnlace() {
        when(credentials.findByLogin("ana@x.cl"))
                .thenReturn(Optional.of(new Credentials(1L, 7L, "ana@x.cl", "hash", "INACTIVE")));

        service.requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4"));

        verifyNoInteractions(mailer, resetTokens);
    }

    @Test
    void elLimitePorIpOCorreoCortaLaSolicitud() {
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("reset-ip:1.2.3.4");

        assertThrows(RateLimitExceededException.class,
                () -> service.requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4")));

        verifyNoInteractions(credentials, mailer);
    }

    @Test
    void cadaSolicitudCuentaParaElLimiteAunqueElCorreoExista() {
        usuarioActivo();

        service.requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4"));

        verify(rateLimiter).recordFailure("reset-ip:1.2.3.4");
        verify(rateLimiter).recordFailure("reset-login:ana@x.cl");
    }

    @Test
    void laBusquedaOcurreFueraDeLaPeticion() {
        Runnable[] pendiente = new Runnable[1];

        service(tarea -> pendiente[0] = tarea)
                .requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4"));

        verifyNoInteractions(credentials, mailer);
        assertNotNull(pendiente[0]);
    }

    @Test
    void unFalloDelCorreoNoSeSaleDelSegundoPlanoYQuedaAuditado() {
        usuarioActivo();
        doThrow(new IllegalStateException("smtp caido")).when(mailer)
                .send(any(), any(), any(), any());

        assertDoesNotThrow(() -> service.requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4")));

        verify(audit).record(eq(new AuditLogger.AuditEvent(
                "PASSWORD_RESET_REQUEST", "ana@x.cl", "FAIL", "1.2.3.4")));
    }

    @Test
    void conLaColaLlenaLaRespuestaNoCambia() {
        Executor lleno = tarea -> { throw new RejectedExecutionException(); };

        assertDoesNotThrow(() -> service(lleno)
                .requestReset(new RequestPasswordResetCommand("ana@x.cl", "1.2.3.4")));
    }
}
