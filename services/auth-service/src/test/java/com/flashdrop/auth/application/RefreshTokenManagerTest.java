package com.flashdrop.auth.application;

import com.flashdrop.auth.application.port.outbound.OpaqueTokenService;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.application.usecase.RefreshTokenManager;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.model.RefreshToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenManagerTest {

    private final RefreshTokenStore store = mock(RefreshTokenStore.class);
    private final OpaqueTokenService opaque = mock(OpaqueTokenService.class);
    private final RefreshTokenManager manager =
            new RefreshTokenManager(store, opaque, Duration.ofDays(30));

    @Test
    void emiteYGuardaSoloElHash() {
        when(opaque.generate()).thenReturn("raw-token");
        when(opaque.hash("raw-token")).thenReturn("hash");

        String raw = manager.issueFor(1L);

        assertEquals("raw-token", raw);
        verify(store).save(argThat(t -> t.tokenHash().equals("hash") && !t.revoked()));
    }

    @Test
    void rotarRevocaElActualYEmiteUnoNuevo() {
        Long userId = 1L;
        var current = new RefreshToken(2L, userId, "old-hash",
                Instant.now().plusSeconds(60), false);
        when(opaque.hash("old-raw")).thenReturn("old-hash");
        when(store.findByTokenHash("old-hash")).thenReturn(Optional.of(current));
        when(opaque.generate()).thenReturn("new-raw");
        when(opaque.hash("new-raw")).thenReturn("new-hash");

        var rotation = manager.rotate("old-raw");

        assertEquals(userId, rotation.userId());
        assertEquals("new-raw", rotation.newRefreshToken());
        verify(store).revoke(argThat(RefreshToken::revoked));
    }

    @Test
    void rotarTokenRevocadoFalla() {
        var revoked = new RefreshToken(1L, 2L, "h",
                Instant.now().plusSeconds(60), true);
        when(opaque.hash("x")).thenReturn("h");
        when(store.findByTokenHash("h")).thenReturn(Optional.of(revoked));
        assertThrows(InvalidTokenException.class, () -> manager.rotate("x"));
    }

    @Test
    void rotarTokenExpiradoFalla() {
        // Vigente en la tabla (revoked = false) pero con expires_at en el pasado.
        var expired = new RefreshToken(3L, 2L, "hash-vencido",
                Instant.now().minusSeconds(1), false);
        when(opaque.hash("vencido")).thenReturn("hash-vencido");
        when(store.findByTokenHash("hash-vencido")).thenReturn(Optional.of(expired));

        assertThrows(InvalidTokenException.class, () -> manager.rotate("vencido"));

        // Un token vencido no debe emitir uno nuevo ni revocar nada.
        verify(store, never()).save(any());
        verify(store, never()).revoke(any());
    }

    @Test
    void rotarTokenInexistenteFalla() {
        when(opaque.hash("desconocido")).thenReturn("no-esta");
        when(store.findByTokenHash("no-esta")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> manager.rotate("desconocido"));
    }
}
