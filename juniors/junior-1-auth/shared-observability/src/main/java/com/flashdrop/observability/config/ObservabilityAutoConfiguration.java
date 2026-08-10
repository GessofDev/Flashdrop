package com.flashdrop.observability.config;

import com.flashdrop.observability.tracing.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
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
}
