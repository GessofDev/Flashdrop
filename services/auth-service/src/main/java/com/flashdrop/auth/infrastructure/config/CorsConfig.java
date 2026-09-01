package com.flashdrop.auth.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Unico lugar donde se configura CORS (hallazgo I-4 de la auditoria QA).
 *
 * <p>Antes convivian dos configuraciones: un {@code CorsFilter} con una lista
 * restringida de origenes, y un {@code CorsConfigurationSource} dentro de
 * SecurityConfig que usaba {@code allowedOriginPatterns("*")} junto con
 * credenciales, combinacion que refleja cualquier origen que lo pida. Con las
 * dos activas, cual prevalecia dependia del orden de los filtros.
 *
 * <p>Ahora hay una sola definicion y Spring Security la toma por nombre de
 * bean. Los origenes vienen por configuracion para que cada entorno declare
 * los suyos; los valores por defecto son los de desarrollo.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins:"
                    + "http://localhost:3000,http://localhost:8080,"
                    + "http://localhost:5173,http://localhost:4200}")
            List<String> allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOrigins, no setAllowedOriginPatterns: la version con
        // comodines admite "*" incluso con credenciales activadas.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Internal-Api-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
