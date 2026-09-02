package com.flashdrop.delivery.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Dedicated {@link RestClient} bean for the internal orders claim
 * adapter ({@code POST /api/internal/orders/claim}).
 *
 * <p>Separate from {@code OrdersServiceRestClientConfig} (which serves the
 * {@code HttpOrderServiceClientAdapter}) for clean isolation:
 * <ul>
 *   <li>Different activation condition ({@code delivery.claim.delegate-to-orders.enabled}
 *       rather than {@code orders.service.url}).</li>
 *   <li>Different defaults — when the feature flag is OFF, this bean is
 *       still instantiated (the adapter itself short-circuits), so the
 *       call site can be wired and tested independently.</li>
 *   <li>Different timeout budget tuned for the claim path (short — courier
 *       waits in front of the phone).</li>
 * </ul>
 *
 * <p>Auth: {@code X-Internal-Api-Key} (loaded from the {@code internal.api.key}
 * Spring property, which is itself bound to the {@code INTERNAL_API_KEY} env
 * var via Spring's relaxed binding and validated at startup by
 * {@code shared-observability}'s {@code ObservabilityAutoConfiguration}).
 */
@Configuration
public class InternalOrdersRestClientConfig {

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
