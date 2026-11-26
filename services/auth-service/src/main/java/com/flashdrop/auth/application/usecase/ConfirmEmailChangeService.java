package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.ConfirmEmailChangeCommand;
import com.flashdrop.auth.application.port.inbound.ConfirmEmailChangeUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.exception.EmailAlreadyInUseException;
import com.flashdrop.auth.domain.exception.InvalidEmailChangeTokenException;
import com.flashdrop.auth.domain.exception.ResourceAlreadyExistsException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Confirms a login email change ({@code POST /auth/email/confirm}).
 * Updates both {@code users.email} and {@code login.login} atomically
 * and revokes all active refresh tokens for the user.
 *
 * <p>The token is validated BEFORE anything is looked up about the target
 * email. The collision check lives inside the transaction, after the token is
 * consumed, so an unauthenticated caller cannot use this endpoint to find out
 * which emails are registered: an invalid token always answers the same.
 */
public class ConfirmEmailChangeService implements ConfirmEmailChangeUseCase {

    private static final String ACTION = "EMAIL_CHANGE_CONFIRM";

    private final EmailChangeTokenStore emailChangeTokens;
    private final OpaqueTokenService opaque;
    private final UserRepository users;
    private final CredentialStore credentials;
    private final RefreshTokenStore refreshTokens;
    private final EmailChangeMailer mailer;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final TransactionalRunner txRunner;
    private final Clock clock;

    public ConfirmEmailChangeService(EmailChangeTokenStore emailChangeTokens, OpaqueTokenService opaque,
                                     UserRepository users, CredentialStore credentials,
                                     RefreshTokenStore refreshTokens, EmailChangeMailer mailer,
                                     RateLimiter rateLimiter, AuditLogger audit,
                                     TransactionalRunner txRunner, Clock clock) {
        this.emailChangeTokens = emailChangeTokens;
        this.opaque = opaque;
        this.users = users;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.txRunner = txRunner;
        this.clock = clock;
    }

    @Override
    public void confirmChange(ConfirmEmailChangeCommand command) {
        String ipKey = "email-change-confirm-ip:" + (command.clientIp() == null ? "unknown" : command.clientIp());
        rateLimiter.checkAllowed(ipKey);
        // Every attempt counts, valid or not: this endpoint is public and each
        // hit may reach the database.
        rateLimiter.recordFailure(ipKey);

        Email newEmail = new Email(command.newEmail());

        Instant now = clock.instant();
        String tokenHash = opaque.hash(command.token());
        AtomicReference<User> updatedUserRef = new AtomicReference<>();

        try {
            txRunner.run(() -> {
                Long userId = emailChangeTokens.consume(tokenHash, now)
                        .orElseThrow(InvalidEmailChangeTokenException::new);

                if (!credentials.isActive(userId)) {
                    // An account pending deletion must not change its email.
                    throw new InvalidEmailChangeTokenException();
                }

                if (users.existsByEmail(newEmail) || credentials.existsByLogin(newEmail.value())) {
                    // Throwing here rolls the transaction back, so the token is
                    // NOT consumed: the user can retry with another email.
                    throw new EmailAlreadyInUseException();
                }

                User user = users.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

                // Only the email column is written: rewriting the whole row from
                // a stale snapshot would revert a profile edit made in parallel.
                users.updateEmail(userId, newEmail.value());
                credentials.updateLogin(userId, newEmail.value());
                refreshTokens.revokeAllForUser(userId);
                emailChangeTokens.invalidateAllForUser(userId, now);
                updatedUserRef.set(user);
            });
        } catch (InvalidEmailChangeTokenException ex) {
            audit.record(new AuditLogger.AuditEvent(ACTION, null, "FAIL", command.clientIp()));
            throw ex;
        } catch (ResourceAlreadyExistsException ex) {
            // Unique race on users.email or login.login: the transaction already
            // rolled back, so the token survives and the client gets the same
            // contract as a plain collision.
            audit.record(new AuditLogger.AuditEvent(ACTION, null, "FAIL", command.clientIp()));
            throw new EmailAlreadyInUseException();
        }

        User updatedUser = updatedUserRef.get();
        audit.record(new AuditLogger.AuditEvent(
                ACTION, String.valueOf(updatedUser.id()), "SUCCESS", command.clientIp()));

        try {
            mailer.sendEmailChangedNotice(newEmail.value(), updatedUser.name());
        } catch (RuntimeException ignored) {
            // Informative notice must not fail an already-committed email change.
        }
    }
}
