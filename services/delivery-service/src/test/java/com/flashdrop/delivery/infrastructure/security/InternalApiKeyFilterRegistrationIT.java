package com.flashdrop.delivery.infrastructure.security;

import com.flashdrop.observability.config.ObservabilityAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ObservabilityAutoConfiguration} fails LOUD in non-dev
 * profiles when {@code internal.api.key} is missing.
 *
 * <p>Previously the bean was guarded by {@code @ConditionalOnProperty}, so a
 * missing key silently registered NO filter — leaving {@code /api/internal/*}
 * open. The PR-A fix makes the bean throw {@link IllegalStateException} at
 * context init time so a missing key is impossible to ship.
 */
@DisplayName("InternalApiKeyFilterRegistrationIT — PR-A: fail-loud on missing internal.api.key")
class InternalApiKeyFilterRegistrationIT {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
            // Tell Spring this is NOT a web app so it doesn't try to scan for
            // @WebApplication / MockMvc setup; we only care about the filter bean.
            .withPropertyValues("spring.main.web-application-type=none");

    @Nested
    @DisplayName("Non-dev profile")
    class NonDevProfile {

        @Test
        @DisplayName("TC1: missing internal.api.key → context fails with IllegalStateException")
        void missingKey_throwsIllegalStateException() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.main.web-application-type=none",
                            "spring.profiles.active=production")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("internal.api.key");
                    });
        }

        @Test
        @DisplayName("TC2: internal.api.key set → context loads, filter is registered")
        void keySet_contextLoads() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.main.web-application-type=none",
                            "spring.profiles.active=production",
                            "internal.api.key=test-key")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        // Both correlationIdFilter and internalApiKeyFilter are
                        // FilterRegistrationBeans; assert the internal one by name.
                        assertThat(context).hasBean("internalApiKeyFilter");
                        assertThat(context).hasBean("correlationIdFilter");
                    });
        }
    }

    @Nested
    @DisplayName("Dev profile")
    class DevProfile {

        @Test
        @DisplayName("TC3: missing internal.api.key under dev → no filter is registered, context loads")
        void missingKey_underDev_doesNotFail() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.main.web-application-type=none",
                            "spring.profiles.active=dev")
                    .run(context -> {
                        // Dev profile exempts the bean entirely — no internalApiKeyFilter
                        // bean at all. Context starts cleanly without the key.
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean("internalApiKeyFilter");
                    });
        }
    }
}