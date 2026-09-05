package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalProductDto;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalRestaurantDto;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cliente HTTP hacia Catalog Service (contratos C-1, C-2, C-3).
 *
 * <p>Orders consume productos/ restaurantes vía API interna en lugar de consultar
 * directamente las tablas {@code products} y {@code restaurant} de Supabase.
 * La conversión UUID ↔ Long es responsabilidad exclusiva de este adapter.</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogHttpClientAdapter implements CatalogPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogHttpClientAdapter.class);
    static final String SERVICE = "Catalog";

    private final RestClient catalogInternalRestClient;

    @Override
    public List<ProductInfo> findProductsByIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        String ids = productIds.stream()
                .map(IdConverter::toLongParam)
                .map(Object::toString)
                .collect(Collectors.joining(","));
        log.debug("Consultando productos internos ids={}", ids);
        try {
            InternalProductDto[] dtos = catalogInternalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/products")
                            .queryParam("ids", ids)
                            .build())
                    .retrieve()
                    .body(InternalProductDto[].class);
            if (dtos == null) {
                return List.of();
            }
            return Arrays.stream(dtos).map(this::toProductInfo).collect(Collectors.toList());
        } catch (HttpStatusCodeException e) {
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    @Override
    public Optional<RestaurantInfo> findRestaurantById(UUID restaurantId) {
        if (restaurantId == null) {
            return Optional.empty();
        }
        long id = IdConverter.toLong(restaurantId);
        log.debug("Consultando restaurante interno id={}", id);
        try {
            InternalRestaurantDto dto = catalogInternalRestClient.get()
                    .uri("/api/internal/restaurants/{id}", id)
                    .retrieve()
                    .body(InternalRestaurantDto.class);
            if (dto == null) {
                return Optional.empty();
            }
            return Optional.of(toRestaurantInfo(dto));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    @Override
    public Optional<UUID> findRestaurantIdByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long uid = IdConverter.toLong(userId);
        log.debug("Consultando restaurante por usuario interno userId={}", uid);
        try {
            InternalRestaurantDto[] dtos = catalogInternalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/restaurants")
                            .queryParam("userId", uid)
                            .build())
                    .retrieve()
                    .body(InternalRestaurantDto[].class);
            if (dtos == null || dtos.length == 0) {
                return Optional.empty();
            }
            return Optional.of(IdConverter.toUuid(dtos[0].id()));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }

    private ProductInfo toProductInfo(InternalProductDto d) {
        return ProductInfo.builder()
                .id(IdConverter.toUuid(d.id()))
                .restaurantId(IdConverter.toUuid(d.restaurantId()))
                .name(d.name())
                .description(d.description())
                .image(d.image())
                .price(d.price())
                .available(d.available() != null && d.available())
                .build();
    }

    private RestaurantInfo toRestaurantInfo(InternalRestaurantDto d) {
        return RestaurantInfo.builder()
                .restaurantId(IdConverter.toUuid(d.id()))
                .name(d.name())
                .address(d.address())
                .build();
    }
}
