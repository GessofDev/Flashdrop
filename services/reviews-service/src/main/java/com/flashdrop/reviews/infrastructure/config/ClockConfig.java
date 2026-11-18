package com.flashdrop.reviews.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del servicio. Inyectarlo (en vez de llamar a {@code Instant.now()})
 * permite congelar el tiempo en los tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
