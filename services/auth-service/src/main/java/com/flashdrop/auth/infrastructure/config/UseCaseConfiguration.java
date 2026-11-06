package com.flashdrop.auth.infrastructure.config;

import com.flashdrop.auth.application.port.inbound.*;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Duration;

/**
 * Cablea los casos de uso (application, sin Spring) con sus adaptadores.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
public class UseCaseConfiguration {

    @Bean
    RefreshTokenManager refreshTokenManager(RefreshTokenStore store, OpaqueTokenService opaque,
                                            JwtProperties props) {
        return new RefreshTokenManager(store, opaque,
                Duration.ofDays(props.getRefreshExpirationDays()));
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, CredentialStore credentials,
                                            RoleRepository roles, PasswordHasher hasher,
                                            AuditLogger audit) {
        return new RegisterUserService(users, credentials, roles, hasher, audit);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(CredentialStore credentials, UserRepository users,
                                                    PasswordHasher hasher, TokenService tokens,
                                                    RefreshTokenManager refreshTokens, RateLimiter rateLimiter,
                                                    AuditLogger audit) {
        return new AuthenticateUserService(credentials, users, hasher, tokens, refreshTokens, rateLimiter, audit);
    }

    @Bean
    RefreshTokenService refreshTokenService(RefreshTokenManager refreshTokens, UserRepository users,
                                            TokenService tokens) {
        return new RefreshTokenService(refreshTokens, users, tokens);
    }

    @Bean
    ValidateTokenUseCase validateTokenUseCase(TokenService tokens, CredentialStore credentials) {
        // Un token firmado no basta: la cuenta debe seguir activa (F5: cuentas en eliminacion quedan fuera).
        return new ActiveAccountTokenValidator(new ValidateTokenService(tokens), credentials);
    }

    @Bean
    GetUserProfileUseCase getUserProfileUseCase(UserRepository users) {
        return new GetUserProfileService(users);
    }

    @Bean
    UpdateUserProfileUseCase updateUserProfileUseCase(UserRepository users) {
        return new UpdateUserProfileService(users);
    }

    @Bean
    GetInternalUserUseCase getInternalUserUseCase(UserRepository users) {
        return new GetInternalUserService(users);
    }

    @Bean
    AssignUserRolesUseCase assignUserRolesUseCase(UserRepository users, RoleRepository roles,
                                                  AuditLogger audit) {
        return new AssignUserRolesService(users, roles, audit);
    }

    @Bean
    AdminUserUseCase adminUserUseCase(UserRepository users, AssignUserRolesUseCase assignUserRoles) {
        return new AdminUserService(users, assignUserRoles);
    }

    @Bean
    RequestPasswordResetUseCase requestPasswordResetUseCase(CredentialStore credentials, UserRepository users,
                                                            PasswordResetTokenStore resetTokens,
                                                            OpaqueTokenService opaque, PasswordResetMailer mailer,
                                                            RateLimiter rateLimiter, AuditLogger audit,
                                                            ThreadPoolTaskExecutor passwordResetExecutor,
                                                            PasswordResetProperties props) {
        return new RequestPasswordResetService(credentials, users, resetTokens, opaque, mailer, rateLimiter,
                audit, passwordResetExecutor, Clock.systemUTC(),
                Duration.ofMinutes(props.getTokenTtlMinutes()), props.getLinkBaseUrl());
    }

    @Bean
    ResetPasswordUseCase resetPasswordUseCase(PasswordResetTokenStore resetTokens, OpaqueTokenService opaque,
                                              CredentialStore credentials, RefreshTokenStore refreshTokens,
                                              PasswordHasher hasher, RateLimiter rateLimiter, AuditLogger audit) {
        return new ResetPasswordService(resetTokens, opaque, credentials, refreshTokens, hasher,
                rateLimiter, audit, Clock.systemUTC());
    }
}
