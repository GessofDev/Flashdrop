package com.flashdrop.auth.infrastructure.config;

import com.flashdrop.auth.application.port.inbound.*;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.application.usecase.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    ValidateTokenUseCase validateTokenUseCase(TokenService tokens) {
        return new ValidateTokenService(tokens);
    }

    @Bean
    GetUserProfileUseCase getUserProfileUseCase(UserRepository users) {
        return new GetUserProfileService(users);
    }
}
