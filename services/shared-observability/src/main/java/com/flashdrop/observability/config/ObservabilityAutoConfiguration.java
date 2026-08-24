package com.flashdrop.observability.config;

import com.flashdrop.observability.security.InternalApiKeyFilter;
import com.flashdrop.observability.tracing.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
     *   <li>Environment property {@code internal.api.key} is set, AND</li>
     *   <li>Active Spring profiles do NOT include {@code dev} (fail-open in dev, fail-closed otherwise)</li>
     * </ul>
     */
    @Bean
    @Profile("!dev")
    @ConditionalOnProperty(name = "internal.api.key")
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter(
            @Value("${internal.api.key}") String apiKey) {
        var registration = new FilterRegistrationBean<>(new InternalApiKeyFilter(apiKey));
        // Run after CorrelationIdFilter (HIGHEST_PRECEDENCE) but before any service-level auth
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/internal/*");
        return registration;
    }
}
