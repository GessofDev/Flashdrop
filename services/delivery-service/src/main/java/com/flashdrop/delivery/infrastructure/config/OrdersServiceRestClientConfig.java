package com.flashdrop.delivery.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient configuration for talking to orders-service over HTTP.
 * Activated when {@code orders.service.url} is set (e.g. via SPRING_PROFILES_ACTIVE=orders
 * or ORDERS_SERVICE_URL env var).
 *
 * Uses the internal API key header for inter-service authentication.
 */
@Configuration
@ConditionalOnProperty(name = "orders.service.url")
public class OrdersServiceRestClientConfig {

    @Value("${orders.service.url}")
    private String ordersServiceUrl;

    @Value("${INTERNAL_API_KEY}")
    private String internalApiKey;

    @Bean
    public RestClient ordersServiceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(ordersServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(factory)
                .build();
    }
}
