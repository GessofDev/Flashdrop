package com.flashdrop.delivery.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa")
@EntityScan(basePackages = "com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity")
public class PersistenceConfig {
}
