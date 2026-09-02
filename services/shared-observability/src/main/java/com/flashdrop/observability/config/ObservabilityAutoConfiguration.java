package com.flashdrop.observability.config;

import com.flashdrop.observability.security.InternalApiKeyFilter;
import com.flashdrop.observability.tracing.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/**
 * Auto-configuración que cualquier servicio activa con solo depender de
 * shared-observability. Registra el filtro de trace_id como el primero de
 * la cadena, para que el id exista desde el inicio del request.
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Registers {@link InternalApiKeyFilter} when:
     * <ul>
     *   <li>Active Spring profiles do NOT include {@code dev} (fail-open in dev, fail-closed otherwise)</li>
     * </ul>
     *
     * <p>The {@code internal.api.key} property is REQUIRED in non-dev profiles
     * — missing it causes the application context to fail at startup with a
     * loud {@link IllegalStateException}, instead of silently registering NO
     * filter and leaving {@code /api/internal/*} open. The previous behaviour
     * ({@code @ConditionalOnProperty(name = "internal.api.key")}) was a
     * fail-open default that allowed unauthenticated access to internal
     * endpoints whenever the env var was forgotten in deployment.
     *
     * <p>The {@code dev} profile is exempt: developers can boot the service
     * without an internal key for local exploration.
     */
    @Bean
    @Profile("!dev")
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter(
            @Value("${internal.api.key:#{null}}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "internal.api.key must be set in non-dev profiles (otherwise /api/internal/* is unauthenticated)");
        }
        var registration = new FilterRegistrationBean<>(new InternalApiKeyFilter(apiKey));
        // Run after CorrelationIdFilter (HIGHEST_PRECEDENCE) but before any service-level auth
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/internal/*");
        return registration;
    }
}
