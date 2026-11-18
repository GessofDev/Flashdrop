package com.flashdrop.reviews.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP hacia orders-service (contrato interno T4).
 *
 * <p>Endpoint consumido: {@code GET /api/internal/orders/{id}}, autenticado con
 * la cabecera {@code X-Internal-Api-Key} (el mismo secret compartido por todos
 * los servicios).</p>
 *
 * <p>Timeout corto a propósito: la escritura de una calificación no puede
 * dejar al usuario esperando indefinidamente por orders-service.</p>
 */
@Configuration
public class OrdersServiceRestClientConfig {

    @Value("${orders.service.url:http://orders-service:8083}")
    private String ordersServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Bean
    public RestClient internalOrdersRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(ordersServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(factory)
                .build();
    }
}
