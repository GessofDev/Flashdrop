package com.flashdrop.auth.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.flashdrop.auth.application.dto.RequestAccountDeletionCommand;
import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort.Blockers;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.PasswordHasher;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.RequestAccountDeletionService;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.exception.AccountDeletionInProgressException;
import com.flashdrop.auth.domain.exception.AccountErasureUnavailableException;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

/** F5: pedir la eliminacion de la cuenta propia. Puertos simulados: sin base ni red. */
class RequestAccountDeletionServiceTest {

    private static final Long USER = 42L;
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final AccountDeletionStore deletions = mock(AccountDeletionStore.class);
    private final AccountErasurePort erasure = mock(AccountErasurePort.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final RequestAccountDeletionService service = new RequestAccountDeletionService(
            users, credentials, hasher, rateLimiter, refreshTokens, deletions, erasure, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private RequestAccountDeletionCommand command(String password) {
        return new RequestAccountDeletionCommand(USER, password, "10.0.0.1");
    }

    private void existingUserWithPassword() {
        when(users.findById(USER)).thenReturn(Optional.of(new User(USER, new Email("ana@correo.cl"), null, "Ana",
                "Perez", null, null, List.of(), NOW)));
        when(credentials.findByLogin("ana@correo.cl"))
                .thenReturn(Optional.of(new Credentials(1L, USER, "ana@correo.cl", "hash", "ACTIVE")));
        when(hasher.matches("Segura1234", "hash")).thenReturn(true);
        when(erasure.checkBlockers(USER)).thenReturn(new Blockers(0, 0, 0));
        when(deletions.create(USER, NOW)).thenReturn(true);
    }

    @Test
    void request_withTheRightPasswordAndNoActiveOrders_disablesTheAccountAndQueuesTheDeletion() {
        existingUserWithPassword();

        service.request(command("Segura1234"));

        InOrder order = inOrder(deletions, credentials, refreshTokens);
        order.verify(deletions).create(USER, NOW);
        order.verify(credentials).setActive(USER, false);
        order.verify(refreshTokens).revokeAllForUser(USER);
        verify(rateLimiter).reset("delete-account:42");
    }

    @Test
    void request_withAWrongPassword_isRejected_andNothingChanges() {
        existingUserWithPassword();
        when(hasher.matches("mala", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.request(command("mala")));

        verify(rateLimiter).recordFailure("delete-account:42");
        verifyNoInteractions(erasure, deletions, refreshTokens);
        verify(credentials, never()).setActive(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void request_withoutPassword_isRejected() {
        existingUserWithPassword();

        assertThrows(InvalidCredentialsException.class, () -> service.request(command(null)));
        verifyNoInteractions(erasure, deletions);
    }

    @Test
    void request_whenRateLimited_stopsBeforeLookingAnything() {
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("delete-account:42");

        assertThrows(RateLimitExceededException.class, () -> service.request(command("x")));
        verifyNoInteractions(users, credentials, hasher, erasure, deletions);
    }

    @Test
    void request_withActiveOrders_isBlocked_nothingIsCancelled_andTheAccountStaysUsable() {
        existingUserWithPassword();
        when(erasure.checkBlockers(USER)).thenReturn(new Blockers(2, 0, 1));

        AccountDeletionBlockedException ex = assertThrows(AccountDeletionBlockedException.class,
                () -> service.request(command("Segura1234")));

        assertTrue(ex.getMessage().contains("como cliente: 2"));
        assertTrue(ex.getMessage().contains("como repartidor: 1"));
        verify(deletions, never()).create(anyLong(), any());
        verify(credentials, never()).setActive(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
    }

    @Test
    void request_whenOrdersDoesNotAnswer_failsWithoutTouchingTheAccount() {
        existingUserWithPassword();
        when(erasure.checkBlockers(USER)).thenThrow(new AccountErasureUnavailableException("Orders no disponible"));

        assertThrows(AccountErasureUnavailableException.class, () -> service.request(command("Segura1234")));

        verify(deletions, never()).create(anyLong(), any());
        verify(credentials, never()).setActive(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void request_whenAlreadyInProgress_isConflict_andTheAccountIsNotTouchedAgain() {
        existingUserWithPassword();
        when(deletions.create(USER, NOW)).thenReturn(false);

        assertThrows(AccountDeletionInProgressException.class, () -> service.request(command("Segura1234")));
        verify(credentials, never()).setActive(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void request_whenDisablingFails_theQueuedRequestIsRemoved() {
        existingUserWithPassword();
        doThrow(new IllegalStateException("base caida")).when(credentials).setActive(USER, false);

        assertThrows(IllegalStateException.class, () -> service.request(command("Segura1234")));

        verify(deletions).delete(USER);
    }

    @Test
    void request_forAnUnknownUser_isNotFound() {
        when(users.findById(USER)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.request(command("x")));
        verify(credentials, never()).findByLogin(anyString());
    }
}
