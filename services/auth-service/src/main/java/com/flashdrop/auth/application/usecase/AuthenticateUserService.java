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

    /**
     * Hash de descarte contra el que se compara cuando el login no existe o
     * esta inactivo (I-1).
     *
     * <p>Sin esto, BCrypt solo se ejecutaba en la rama del usuario existente y
     * la diferencia de tiempo de respuesta permitia averiguar que correos estan
     * registrados. Se calcula una sola vez al construir el servicio.
     */
    private final String hashDeDescarte;

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
        this.hashDeDescarte = hasher.hash("contrasena-inexistente-para-tiempo-constante");
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

        // I-1: se ejecuta un BCrypt en los dos caminos. Si no hay credenciales
        // utilizables se compara contra el hash de descarte, de modo que el
        // tiempo de respuesta no revele si el correo existe. El resultado de esa
        // comparacion se descarta: la decision la toma `utilizable`.
        boolean utilizable = found.isPresent() && found.get().isActive();
        String hashAComparar = utilizable ? found.get().passwordHash() : hashDeDescarte;
        boolean claveCorrecta = hasher.matches(command.rawPassword(), hashAComparar);

        // S-07: mismo error para login inexistente, inactivo o clave incorrecta.
        if (!utilizable || !claveCorrecta) {
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
