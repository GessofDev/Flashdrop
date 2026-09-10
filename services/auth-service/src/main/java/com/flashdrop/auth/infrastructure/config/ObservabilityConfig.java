package com.flashdrop.auth.infrastructure.config;

import com.flashdrop.observability.tracing.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el filtro de correlacion de shared-observability.
 *
 * <p><b>Por que existe este archivo.</b> shared-observability incorporo una
 * {@code ObservabilityAutoConfiguration} que expone dos beans: el filtro de
 * correlacion y un {@code internalApiKeyFilter}. El segundo choca por nombre
 * con el filtro propio de auth, y ademas responde distinto:
 *
 * <pre>
 *   compartido : 401 UNAUTHORIZED + ApiError, con la ruta en el mensaje
 *   auth       : 403 FORBIDDEN    + {status, error, message}
 * </pre>
 *
 * <p>El formato de auth es el que exige la seccion 10 del plan de migracion
 * para los endpoints entre microservicios, el que valido QA al cerrar el
 * hallazgo I-3, el que fijan sus tests de contrato, y el mismo que ya devuelve
 * catalog-service. Cambiarlo aca romperia a los consumidores.
 *
 * <p>Por eso auth excluye esa autoconfiguracion y vuelve a registrar el filtro
 * de correlacion, que si le interesa. Es una solucion temporal: lo correcto es
 * que el equipo decida un unico formato y que el filtro compartido lo adopte,
 * momento en el cual esta clase y el filtro local de auth desaparecen. Queda
 * planteado en el pull request.
 *
 * <p>La exclusion se declara en application.yml, no aca.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
