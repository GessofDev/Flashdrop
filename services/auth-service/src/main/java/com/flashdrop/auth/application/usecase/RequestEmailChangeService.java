package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.RequestEmailChangeCommand;
import com.flashdrop.auth.application.port.inbound.RequestEmailChangeUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.EmailChangeToken;
import com.flashdrop.auth.domain.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Initiates a login email change ({@code POST /auth/email/change-request}).
 * Verifies the user's current password and sends a single-use link to the
 * user's current email address.
 *
 * <p>Every request counts against the rate limit, including the successful
 * ones: each one can put an email on the wire, so allowing unlimited
 * successful requests is a mail-flooding primitive.
 */
public class RequestEmailChangeService implements RequestEmailChangeUseCase {

    private static final String ACTION = "EMAIL_CHANGE_REQUEST";

    private final UserRepository users;
    private final CredentialStore credentials;
    private final PasswordHasher hasher;
    private final EmailChangeTokenStore emailChangeTokens;
    private final OpaqueTokenService opaque;
    private final EmailChangeMailer mailer;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String linkBaseUrl;

    public RequestEmailChangeService(UserRepository users, CredentialStore credentials,
                                     PasswordHasher hasher, EmailChangeTokenStore emailChangeTokens,
                                     OpaqueTokenService opaque, EmailChangeMailer mailer,
                                     RateLimiter rateLimiter, AuditLogger audit,
                                     Clock clock, Duration tokenTtl, String linkBaseUrl) {
        this.users = users;
        this.credentials = credentials;
        this.hasher = hasher;
        this.emailChangeTokens = emailChangeTokens;
        this.opaque = opaque;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void requestChange(RequestEmailChangeCommand command) {
        String userKey = String.valueOf(command.userId());
        String limitKey = "email-change-req:" + userKey;
        String ipKey = "email-change-ip:" + (command.clientIp() == null ? "unknown" : command.clientIp());

        rateLimiter.checkAllowed(limitKey);
        rateLimiter.checkAllowed(ipKey);
        // Both buckets count every attempt: a valid password still triggers
        // an outgoing email, so it must not be free.
        rateLimiter.recordFailure(limitKey);
        rateLimiter.recordFailure(ipKey);

        User user = users.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + command.userId()));

        Optional<Credentials> stored = credentials.findByUserId(command.userId())
                .filter(Credentials::isActive);
        boolean passwordOk = stored.isPresent()
                && command.password() != null
                && hasher.matches(command.password(), stored.get().passwordHash());

        if (!passwordOk) {
            audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "FAIL", command.clientIp()));
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        emailChangeTokens.invalidateAllForUser(user.id(), now);

        String rawToken = opaque.generate();
        emailChangeTokens.save(new EmailChangeToken(user.id(), opaque.hash(rawToken), now.plus(tokenTtl)));

        try {
            mailer.sendConfirmationLink(
                    user.email().value(),
                    user.name(),
                    linkBaseUrl + "#token=" + rawToken,
                    tokenTtl);
        } catch (RuntimeException e) {
            // The link never went out: without this, a failed SMTP would look
            // exactly like a request that never happened. The pending token is
            // dropped so it cannot be used later.
            emailChangeTokens.invalidateAllForUser(user.id(), clock.instant());
            audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "FAIL", command.clientIp()));
            throw e;
        }

        audit.record(new AuditLogger.AuditEvent(ACTION, userKey, "SUCCESS", command.clientIp()));
    }
}
