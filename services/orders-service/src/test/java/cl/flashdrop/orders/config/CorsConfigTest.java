package cl.flashdrop.orders.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.cors.CorsConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El origen permitido se configura por propiedad, no por codigo: cada
 * entorno declara los suyos (gateway, frontends locales, frontend publicado).
 * Estos tests fijan el comportamiento de esa lista.
 */
class CorsConfigTest {

    private static final String PRODUCTION_ORIGIN = "http://web.76-13-168-23.sslip.io";

    @Test
    void shouldAllowOriginDeclaredInTheProperty() {
        CorsConfiguration cors = new CorsConfig(List.of("http://localhost:5173", PRODUCTION_ORIGIN))
                .corsConfiguration();

        assertEquals(PRODUCTION_ORIGIN, cors.checkOrigin(PRODUCTION_ORIGIN));
    }

    @Test
    void shouldRejectOriginThatIsNotDeclared() {
        CorsConfiguration cors = new CorsConfig(List.of("http://localhost:5173", PRODUCTION_ORIGIN))
                .corsConfiguration();

        assertNull(cors.checkOrigin("http://evil.example"));
    }

    @Test
    void shouldAllowTheMethodsAndTheCredentialsOfTheApp() {
        CorsConfiguration cors = new CorsConfig(List.of(PRODUCTION_ORIGIN)).corsConfiguration();

        assertTrue(cors.getAllowedMethods()
                .containsAll(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")));
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
    }

    /**
     * La propiedad que lee el filtro tiene que estar declarada en el archivo
     * de configuracion: si los nombres no coinciden, la variable de entorno
     * se ignora en silencio y el servicio vuelve a permitir solo los origenes
     * de desarrollo.
     */
    @Test
    void shouldDeclareTheAllowedOriginsInTheConfigurationFile() throws IOException {
        Parameter parameter = CorsConfig.class.getDeclaredConstructors()[0].getParameters()[0];
        String placeholder = parameter.getAnnotation(Value.class).value();
        int keyEnd = placeholder.indexOf(':');
        String propertyKey =
                placeholder.startsWith("${") && keyEnd > 0 ? placeholder.substring(2, keyEnd) : placeholder;

        Properties properties = new Properties();
        try (InputStream stream = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(stream);
        }

        assertTrue(properties.containsKey(propertyKey),
                "application.properties must declare '" + propertyKey + "'");
    }
}
