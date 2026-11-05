package com.flashdrop.catalog.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hora de los locales. Los horarios se escriben en hora local, así que la zona
 * es explícita: el contenedor corre en UTC y sin esto se abrirían 3 horas antes.
 */
@Configuration
public class StoreTimeConfig {

    @Bean
    Clock storeClock(@Value("${catalog.store-timezone:America/Santiago}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
