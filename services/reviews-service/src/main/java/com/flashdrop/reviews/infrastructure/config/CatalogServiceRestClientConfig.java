package com.flashdrop.reviews.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP hacia catalog-service.
 *
 * <p>Endpoint consumido: {@code GET /api/internal/restaurants?userId={userId}},
 * autenticado con {@code X-Internal-Api-Key}. Sirve para resolver de qué
 * tienda es dueño un usuario con rol {@code Restaurante} y validar así su
 * participación en la orden (matriz §4).</p>
 */
@Configuration
public class CatalogServiceRestClientConfig {

    @Value("${catalog.service.url:http://catalog-service:8082}")
    private String catalogServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Bean
    public RestClient internalCatalogRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(catalogServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(factory)
                .build();
    }
}
