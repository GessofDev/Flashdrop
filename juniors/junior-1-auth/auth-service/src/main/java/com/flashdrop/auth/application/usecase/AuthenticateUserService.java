package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.AuthResult;
import com.flashdrop.auth.application.dto.AuthenticateCommand;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.AuthenticateUserUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.User;

import java.util.Optional;

public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final CredentialStore credentials;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final RefreshTokenManager refreshTokens;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;

    public AuthenticateUserService(CredentialStore credentials, UserRepository users, PasswordHasher hasher,
                                   TokenService tokens, RefreshTokenManager refreshTokens,
                                   RateLimiter rateLimiter, AuditLogger audit) {
        this.credentials = credentials;
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Override
    public AuthResult authenticate(AuthenticateCommand command) {
        String login = command.login() == null ? "" : command.login().trim().toLowerCase();
        String ipKey = "ip:" + (command.clientIp() == null ? "unknown" : command.clientIp());
        String loginKey = "login:" + login;

        // S-08: bloquea si ya hubo demasiados intentos por IP o por cuenta.
        rateLimiter.checkAllowed(ipKey);
        rateLimiter.checkAllowed(loginKey);

        Optional<Credentials> found = credentials.findByLogin(login);
        // S-07: mismo error para login inexistente, inactivo o clave incorrecta.
        if (found.isEmpty() || !found.get().isActive()
                || !hasher.matches(command.rawPassword(), found.get().passwordHash())) {
            rateLimiter.recordFailure(ipKey);
            rateLimiter.recordFailure(loginKey);
            audit.record(new AuditLogger.AuditEvent("LOGIN", login, "FAIL", command.clientIp()));
            throw new InvalidCredentialsException();
        }

        User user = users.findById(found.get().userId()).orElseThrow(InvalidCredentialsException::new);

        rateLimiter.reset(ipKey);
        rateLimiter.reset(loginKey);

        String access = tokens.issue(new TokenClaims(user.id(), user.email().value(), user.roleNames()));
        String refresh = refreshTokens.issueFor(user.id());
        audit.record(new AuditLogger.AuditEvent("LOGIN", login, "SUCCESS", command.clientIp()));

        return new AuthResult(user.id(), user.name(), user.email().value(), user.roleNames(),
                access, refresh, tokens.accessTtlSeconds());
    }
}
