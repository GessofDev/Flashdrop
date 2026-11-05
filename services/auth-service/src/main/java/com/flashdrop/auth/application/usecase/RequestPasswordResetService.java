package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.RequestPasswordResetCommand;
import com.flashdrop.auth.application.port.inbound.RequestPasswordResetUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.PasswordResetToken;
import com.flashdrop.auth.domain.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Solicitud de recuperación de contraseña ({@code POST /auth/password/forgot}).
 *
 * <p>La respuesta es la misma exista o no el correo (S-07), y todo el trabajo
 * se hace en segundo plano. Si la búsqueda y el envío fueran en la petición,
 * el tiempo de respuesta delataría qué correos están registrados.
 *
 * <p>Cada solicitud cuenta para el límite por IP y por correo, porque aquí no
 * hay fallos que contar: cada una puede mandar un correo.
 */
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

    private static final String ACTION = "PASSWORD_RESET_REQUEST";

    private final CredentialStore credentials;
    private final UserRepository users;
    private final PasswordResetTokenStore resetTokens;
    private final OpaqueTokenService opaque;
    private final PasswordResetMailer mailer;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final Executor executor;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String linkBaseUrl;

    public RequestPasswordResetService(CredentialStore credentials, UserRepository users,
                                       PasswordResetTokenStore resetTokens, OpaqueTokenService opaque,
                                       PasswordResetMailer mailer, RateLimiter rateLimiter,
                                       AuditLogger audit, Executor executor, Clock clock,
                                       Duration tokenTtl, String linkBaseUrl) {
        this.credentials = credentials;
        this.users = users;
        this.resetTokens = resetTokens;
        this.opaque = opaque;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.executor = executor;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void requestReset(RequestPasswordResetCommand command) {
        String login = command.email() == null ? "" : command.email().trim().toLowerCase();
        String ipKey = "reset-ip:" + (command.clientIp() == null ? "unknown" : command.clientIp());
        String loginKey = "reset-login:" + login;

        rateLimiter.checkAllowed(ipKey);
        rateLimiter.checkAllowed(loginKey);
        rateLimiter.recordFailure(ipKey);
        rateLimiter.recordFailure(loginKey);

        try {
            executor.execute(() -> process(login, command.clientIp()));
        } catch (RejectedExecutionException e) {
            audit.record(new AuditLogger.AuditEvent(ACTION, login, "FAIL", command.clientIp()));
        }
    }

    private void process(String login, String clientIp) {
        try {
            Optional<Credentials> found = credentials.findByLogin(login).filter(Credentials::isActive);
            Optional<User> user = found.flatMap(c -> users.findById(c.userId()));
            if (user.isEmpty()) {
                audit.record(new AuditLogger.AuditEvent(ACTION, login, "FAIL", clientIp));
                return;
            }

            Instant now = clock.instant();
            resetTokens.invalidateAllForUser(user.get().id(), now);

            String rawToken = opaque.generate();
            resetTokens.save(new PasswordResetToken(user.get().id(), opaque.hash(rawToken), now.plus(tokenTtl)));

            // El token va en el fragmento (#): el navegador no lo envía al servidor,
            // así no queda en los registros de Nginx ni en el encabezado Referer.
            mailer.send(user.get().email().value(), user.get().name(), linkBaseUrl + "#token=" + rawToken, tokenTtl);
            audit.record(new AuditLogger.AuditEvent(ACTION, login, "SUCCESS", clientIp));
        } catch (RuntimeException e) {
            // Corre en segundo plano: nadie más va a ver este fallo.
            audit.record(new AuditLogger.AuditEvent(ACTION, login, "FAIL", clientIp));
        }
    }
}
