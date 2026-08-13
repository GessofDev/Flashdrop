package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.AuthResult;
import com.flashdrop.auth.application.dto.AuthenticateCommand;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.AuthenticateUserService;
import com.flashdrop.auth.application.usecase.RefreshTokenManager;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticateUserServiceTest {

    private final CredentialStore credentials = mock(CredentialStore.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final TokenService tokens = mock(TokenService.class);
    private final RefreshTokenManager refreshTokens = mock(RefreshTokenManager.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private final AuthenticateUserService service = new AuthenticateUserService(
            credentials, users, hasher, tokens, refreshTokens, rateLimiter, audit);

    @Test
    void loginInexistenteLanzaCredencialesInvalidas() {
        when(credentials.findByLogin(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate(new AuthenticateCommand("nadie@x.cl", "12345678", "1.2.3.4")));
        verifyNoInteractions(tokens);
        verify(rateLimiter).recordFailure("login:nadie@x.cl");
    }

    @Test
    void passwordIncorrectoLanzaElMismoErrorGenerico() {
        var creds = new Credentials(1L, 1L, "user@x.cl", "hash", "ACTIVE");
        when(credentials.findByLogin("user@x.cl")).thenReturn(Optional.of(creds));
        when(hasher.matches("mala", "hash")).thenReturn(false);
        assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate(new AuthenticateCommand("user@x.cl", "mala", "1.2.3.4")));
    }

    @Test
    void loginCorrectoDevuelveAccessYRefresh() {
        Long userId = 1L;
        var creds = new Credentials(1L, userId, "user@x.cl", "hash", "ACTIVE");
        var user = new User(userId, new Email("user@x.cl"), null, "Ana", "P", null, null, List.of(), Instant.now());
        when(credentials.findByLogin("user@x.cl")).thenReturn(Optional.of(creds));
        when(hasher.matches("buena", "hash")).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(tokens.issue(any())).thenReturn("access-jwt");
        when(tokens.accessTtlSeconds()).thenReturn(900L);
        when(refreshTokens.issueFor(userId)).thenReturn("refresh-opaco");

        AuthResult result = service.authenticate(new AuthenticateCommand("user@x.cl", "buena", "1.2.3.4"));

        assertEquals("access-jwt", result.accessToken());
        assertEquals("refresh-opaco", result.refreshToken());
        assertEquals(900L, result.expiresInSeconds());
        verify(rateLimiter).reset("login:user@x.cl");
    }
}
