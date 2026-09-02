package cl.flashdrop.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP entre servicios internos de Orders.
 *
 * <p>Expose {@link RestClient} beans para catalog-service, auth-service y delivery-service,
 * configurados con la URL del servicio y la cabecera {@code X-Internal-Api-Key}
 * para autenticar las llamadas entre servicios ({@code /api/internal/*}).</p>
 *
 * <p>ORD-F5: connect/read timeout explícitos — sin esto, un servicio dependiente lento
 * (no caído, solo lento) cuelga el hilo indefinidamente en vez de fallar con el
 * {@code ExternalServiceException}/503 que MIGRATION_PLAN.md §12.2 exige para el caso
 * "Catalog caído o timeout". Valores alineados con los que ya usa Delivery Service
 * (OrdersServiceRestClientConfig: connect 5s, read 10s).</p>
 */
@Configuration
public class InternalServiceClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
