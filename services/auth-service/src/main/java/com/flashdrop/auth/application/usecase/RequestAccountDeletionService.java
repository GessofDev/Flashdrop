package com.flashdrop.auth.application.usecase;

import java.time.Clock;
import java.util.Optional;

import com.flashdrop.auth.application.dto.RequestAccountDeletionCommand;
import com.flashdrop.auth.application.port.inbound.RequestAccountDeletionUseCase;
import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.PasswordHasher;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.exception.AccountDeletionInProgressException;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.User;

/**
 * Eliminar la cuenta propia (F5). Quien tiene la contrasena puede eliminarla. Mientras haya pedidos o
 * rutas en curso se rechaza (409): nada se cancela automaticamente, la persona debe cerrarlos antes.
 * Al aceptar, la cuenta queda inhabilitada y sin sesiones, y el borrado en los demas servicios continua
 * en segundo plano con reintentos ({@link ProcessAccountDeletionService}).
 */
public class RequestAccountDeletionService implements RequestAccountDeletionUseCase {

    private static final String ACTION = "ACCOUNT_DELETION_REQUEST";

    private final UserRepository users;
    private final CredentialStore credentials;
    private final PasswordHasher hasher;
    private final RateLimiter rateLimiter;
    private final RefreshTokenStore refreshTokens;
    private final AccountDeletionStore deletions;
    private final AccountErasurePort erasure;
    private final AuditLogger audit;
    private final Clock clock;

    public RequestAccountDeletionService(UserRepository users, CredentialStore credentials, PasswordHasher hasher,
                                         RateLimiter rateLimiter, RefreshTokenStore refreshTokens,
                                         AccountDeletionStore deletions, AccountErasurePort erasure,
                                         AuditLogger audit, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.refreshTokens = refreshTokens;
        this.deletions = deletions;
        this.erasure = erasure;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void request(RequestAccountDeletionCommand command) {
        String userKey = String.valueOf(command.userId());
        String limitKey = "delete-account:" + userKey;
        rateLimiter.checkAllowed(limitKey);

        User user = users.findById(command.userId()).orElseThrow(() -> new UserNotFoundException(
                "User not found with id: " + command.userId()));
        Optional<Credentials> stored = credentials.findByLogin(user.email().value());
        boolean passwordOk = stored.isPresent() && command.password() != null
                && hasher.matches(command.password(), stored.get().passwordHash());
        if (!passwordOk) {
            rateLimiter.recordFailure(limitKey);
            audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "FAIL", command.clientIp()));
            throw new InvalidCredentialsException();
        }
        rateLimiter.reset(limitKey);

        AccountErasurePort.Blockers blockers = erasure.checkBlockers(command.userId());
        if (blockers.total() > 0) {
            audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "BLOCKED", command.clientIp()));
            throw new AccountDeletionBlockedException(blockedMessage(blockers));
        }

        if (!deletions.create(command.userId(), clock.instant())) {
            throw new AccountDeletionInProgressException();
        }
        try {
            credentials.setActive(command.userId(), false);
            refreshTokens.revokeAllForUser(command.userId());
        } catch (RuntimeException e) {
            deletions.delete(command.userId());
            throw e;
        }
        audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "SUCCESS", command.clientIp()));
    }

    static String blockedMessage(AccountErasurePort.Blockers blockers) {
        return "No puedes eliminar tu cuenta mientras tengas pedidos en curso (como cliente: "
                + blockers.asClient() + ", como tienda: " + blockers.asRestaurant()
                + ", como repartidor: " + blockers.asCourier()
                + "). Cancelalos o espera a que terminen e intenta de nuevo.";
    }
}
