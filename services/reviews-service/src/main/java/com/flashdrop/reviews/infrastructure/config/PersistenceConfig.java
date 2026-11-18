package com.flashdrop.reviews.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Mapeo de las entidades y repositorios JPA de reviews-service.
 *
 * <p>Espejo de {@code delivery-service/infrastructure/config/PersistenceConfig}:
 * las entidades viven en {@code ...persistence.jpa.entity} y los repositorios
 * Spring Data en {@code ...persistence.jpa.repository}.</p>
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa")
@EntityScan(basePackages = "com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.entity")
public class PersistenceConfig {
}
