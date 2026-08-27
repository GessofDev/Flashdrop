package cl.flashdrop.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP entre servicios internos de Orders.
 *
 * <p>Expose {@link RestClient} beans para catalog-service, auth-service y delivery-service,
 * configurados con la URL del servicio y la cabecera {@code X-Internal-Api-Key}
 * para autenticar las llamadas entre servicios ({@code /api/internal/*}).</p>
 */
@Configuration
public class InternalServiceClientConfig {

    @Bean
    public RestClient catalogInternalRestClient(
            @Value("${catalog.service.url}") String baseUrl,
            @Value("${internal.api.key}") String apiKey) {
        return build(baseUrl, apiKey);
    }

    @Bean
    public RestClient authInternalRestClient(
            @Value("${auth.service.url}") String baseUrl,
            @Value("${internal.api.key}") String apiKey) {
        return build(baseUrl, apiKey);
    }

    @Bean
    public RestClient deliveryInternalRestClient(
            @Value("${delivery.service.url}") String baseUrl,
            @Value("${internal.api.key}") String apiKey) {
        return build(baseUrl, apiKey);
    }

    private static RestClient build(String baseUrl, String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
