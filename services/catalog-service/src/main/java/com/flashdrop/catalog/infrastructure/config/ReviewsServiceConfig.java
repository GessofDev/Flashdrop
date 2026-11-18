package com.flashdrop.catalog.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de reviews-service.
 *
 * <p>El header {@code X-Internal-Api-Key} lo agrega el adapter en cada
 * pedido ({@code HttpRatingAggregateClientAdapter}); aca solo se define la URL
 * base y el presupuesto de tiempo.
 *
 * <p>La URL sale de {@code catalog.reviews.base-url} y el secret de
 * {@code catalog.reviews.api-key} (que por defecto es el
 * {@code internal.api.key} compartido por todos los servicios).
 */
@Configuration
public class ReviewsServiceConfig {

    @Value("${catalog.reviews.base-url:http://reviews-service:8085}")
    private String reviewsServiceUrl;

    @Bean
    public RestClient reviewsServiceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(reviewsServiceUrl)
                .requestFactory(factory)
                .build();
    }
}
