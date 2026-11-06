package com.flashdrop.auth.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashdrop.auth.application.port.inbound.ProcessAccountDeletionUseCase;
import com.flashdrop.auth.application.port.inbound.RequestAccountDeletionUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.PasswordHasher;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.ProcessAccountDeletionService;
import com.flashdrop.auth.application.usecase.RequestAccountDeletionService;

/** Cableado de la eliminacion de cuentas (F5) y de su proceso en segundo plano. */
@Configuration
@EnableScheduling
public class AccountDeletionConfig {

    @Bean
    RequestAccountDeletionUseCase requestAccountDeletionUseCase(
            UserRepository users, CredentialStore credentials, PasswordHasher hasher, RateLimiter rateLimiter,
            RefreshTokenStore refreshTokens, AccountDeletionStore deletions, AccountErasurePort erasure,
            AuditLogger audit) {
        return new RequestAccountDeletionService(users, credentials, hasher, rateLimiter, refreshTokens,
                deletions, erasure, audit, Clock.systemUTC());
    }

    @Bean
    ProcessAccountDeletionUseCase processAccountDeletionUseCase(
            AccountDeletionStore deletions, AccountErasurePort erasure, UserRepository users,
            CredentialStore credentials, AuditLogger audit,
            @Value("${auth.account-deletion.max-attempts:20}") int maxAttempts) {
        return new ProcessAccountDeletionService(deletions, erasure, users, credentials, audit,
                Clock.systemUTC(), maxAttempts);
    }

    @Component
    @ConditionalOnProperty(name = "auth.account-deletion.enabled", havingValue = "true", matchIfMissing = true)
    static class AccountDeletionScheduler {

        private final ProcessAccountDeletionUseCase process;

        AccountDeletionScheduler(ProcessAccountDeletionUseCase process) {
            this.process = process;
        }

        @Scheduled(fixedDelayString = "${auth.account-deletion.poll-ms:30000}",
                initialDelayString = "${auth.account-deletion.poll-ms:30000}")
        void run() {
            process.processDue();
        }
    }
}
