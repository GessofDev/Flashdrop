package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.ResetPasswordCommand;
import com.flashdrop.auth.application.port.inbound.ResetPasswordUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.PasswordPolicy;
import com.flashdrop.auth.domain.exception.InvalidResetTokenException;

import java.time.Clock;
import java.time.Instant;

/**
 * Cambio de contraseña con el enlace del correo ({@code POST /auth/password/reset}).
 *
 * <p>La política se valida antes de consumir el token: una clave débil no debe
 * gastar el enlace. Al terminar se revocan los refresh tokens, porque quien
 * recupera su clave puede estar sacando a otra persona de su cuenta.
 */
public class ResetPasswordService implements ResetPasswordUseCase {

    private final PasswordResetTokenStore resetTokens;
    private final OpaqueTokenService opaque;
    private final CredentialStore credentials;
    private final RefreshTokenStore refreshTokens;
    private final PasswordHasher hasher;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final Clock clock;

    public ResetPasswordService(PasswordResetTokenStore resetTokens, OpaqueTokenService opaque,
                                CredentialStore credentials, RefreshTokenStore refreshTokens,
                                PasswordHasher hasher, RateLimiter rateLimiter,
                                AuditLogger audit, Clock clock) {
        this.resetTokens = resetTokens;
        this.opaque = opaque;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void reset(ResetPasswordCommand command) {
        String ipKey = "reset-ip:" + (command.clientIp() == null ? "unknown" : command.clientIp());
        rateLimiter.checkAllowed(ipKey);

        PasswordPolicy.validate(command.newPassword());

        Instant now = clock.instant();
        Long userId = resetTokens.consume(opaque.hash(command.token()), now).orElseThrow(() -> {
            rateLimiter.recordFailure(ipKey);
            audit.record(new AuditLogger.AuditEvent("PASSWORD_RESET", null, "FAIL", command.clientIp()));
            return new InvalidResetTokenException();
        });

        credentials.updatePassword(userId, hasher.hash(command.newPassword()));
        refreshTokens.revokeAllForUser(userId);
        resetTokens.invalidateAllForUser(userId, now);
        audit.record(new AuditLogger.AuditEvent("PASSWORD_RESET", String.valueOf(userId), "SUCCESS", command.clientIp()));
    }
}
