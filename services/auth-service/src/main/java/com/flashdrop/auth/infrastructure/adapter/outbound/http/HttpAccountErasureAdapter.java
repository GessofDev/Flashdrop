package com.flashdrop.auth.infrastructure.adapter.outbound.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.exception.AccountErasureUnavailableException;

/**
 * Cliente HTTP (HTTP/1.1, con timeouts) hacia los endpoints internos de eliminacion de Orders, Delivery y
 * Catalog, autenticado con X-Internal-Api-Key. Los servicios se resuelven por nombre dentro de la red de FloCI.
 */
@Component
public class HttpAccountErasureAdapter implements AccountErasurePort {

    private final RestClient orders;
    private final RestClient delivery;
    private final RestClient catalog;

    public HttpAccountErasureAdapter(
            @Value("${services.orders-url:http://orders-service:8083}") String ordersUrl,
            @Value("${services.delivery-url:http://delivery-service:8084}") String deliveryUrl,
            @Value("${services.catalog-url:http://catalog-service:8082}") String catalogUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this.orders = client(ordersUrl, apiKey);
        this.delivery = client(deliveryUrl, apiKey);
        this.catalog = client(catalogUrl, apiKey);
    }

    private static RestClient client(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private record BlockersDto(int asClient, int asRestaurant, int asCourier) {
    }

    @Override
    public Blockers checkBlockers(Long userId) {
        try {
            BlockersDto dto = orders.get()
                    .uri("/api/internal/users/{id}/deletion-blockers", userId)
                    .retrieve()
                    .body(BlockersDto.class);
            if (dto == null) {
                throw new AccountErasureUnavailableException("Orders respondio sin contenido");
            }
            return new Blockers(dto.asClient(), dto.asRestaurant(), dto.asCourier());
        } catch (HttpStatusCodeException e) {
            throw unavailable("Orders", e);
        } catch (ResourceAccessException e) {
            throw new AccountErasureUnavailableException("Orders no disponible", e);
        }
    }

    @Override
    public void eraseOrders(Long userId) {
        try {
            orders.post().uri("/api/internal/users/{id}/erase", userId).retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new AccountDeletionBlockedException("Aparecieron pedidos en curso antes de completar la eliminacion");
            }
            throw unavailable("Orders", e);
        } catch (ResourceAccessException e) {
            throw new AccountErasureUnavailableException("Orders no disponible", e);
        }
    }

    @Override
    public void eraseDelivery(Long userId) {
        try {
            delivery.post().uri("/api/internal/erasure/delivery-persons?userId={id}", userId)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw unavailable("Delivery", e);
        } catch (ResourceAccessException e) {
            throw new AccountErasureUnavailableException("Delivery no disponible", e);
        }
    }

    @Override
    public void eraseCatalog(Long userId) {
        try {
            catalog.post().uri("/api/internal/erasure/restaurant-owner?userId={id}", userId)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw unavailable("Catalog", e);
        } catch (ResourceAccessException e) {
            throw new AccountErasureUnavailableException("Catalog no disponible", e);
        }
    }

    private static AccountErasureUnavailableException unavailable(String service, HttpStatusCodeException e) {
        return new AccountErasureUnavailableException(service + " respondio " + e.getStatusCode().value(), e);
    }
}
