package cl.flashdrop.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del sistema como bean, para que los casos de uso que calculan rangos de fecha
 * (resumen de ventas) se puedan probar con un {@link Clock#fixed} en vez de "ahora".
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
