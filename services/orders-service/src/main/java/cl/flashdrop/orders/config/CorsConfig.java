package cl.flashdrop.orders.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Configuración global de CORS para orders-service.
 *
 * Los orígenes permitidos se declaran por propiedad
 * (`orders.cors.allowed-origins`, lista separada por comas) para que cada
 * entorno declare los suyos sin recompilar: el gateway, los frontends
 * locales y el frontend publicado. El origen viaja en cada pedido del
 * navegador, así que un origen que no está en la lista se rechaza con 403
 * antes de resolver el pedido.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${orders.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4200}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration());

        return new CorsFilter(source);
    }

    /**
     * Configuración de CORS que aplica el filtro.
     *
     * @return la configuración con los orígenes declarados por propiedad.
     */
    CorsConfiguration corsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        return config;
    }
}
