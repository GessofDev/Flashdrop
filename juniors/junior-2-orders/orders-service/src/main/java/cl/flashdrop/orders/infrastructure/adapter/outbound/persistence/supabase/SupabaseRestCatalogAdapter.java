package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase;

import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.infrastructure.persistence.dto.ProductRow;
import cl.flashdrop.orders.infrastructure.persistence.dto.RestaurantRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SupabaseRestCatalogAdapter implements CatalogPort {

    private final RestClient supabaseRestClient;

    @Override
    public List<ProductInfo> findProductsByIds(List<UUID> productIds) {
        String ids = productIds.stream().map(this::extractRawId).collect(Collectors.joining(","));
        ProductRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/products").queryParam("id", "in.(" + ids + ")")
                        .queryParam("select", "*").build())
                .retrieve().body(ProductRow[].class);
        if (rows == null) return List.of();
        return Arrays.stream(rows).map(this::toProductInfo).collect(Collectors.toList());
    }

    @Override
    public Optional<RestaurantInfo> findRestaurantById(UUID restaurantId) {
        RestaurantRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/restaurant").queryParam("id", "eq." + extractRawId(restaurantId))
                        .queryParam("select", "*").build())
                .retrieve().body(RestaurantRow[].class);
        if (rows == null || rows.length == 0) return Optional.empty();
        RestaurantRow r = rows[0];
        return Optional.of(RestaurantInfo.builder().restaurantId(toUuid(r.id())).name(r.name()).address(r.address()).build());
    }

    @Override
    public Optional<UUID> findRestaurantIdByUserId(UUID userId) {
        RestaurantRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/restaurant").queryParam("user_id", "eq." + extractRawId(userId))
                        .queryParam("select", "id").build())
                .retrieve().body(RestaurantRow[].class);
        if (rows == null || rows.length == 0) return Optional.empty();
        return Optional.of(toUuid(rows[0].id()));
    }

    private String extractRawId(UUID uuid) {
        return uuid.getLeastSignificantBits() <= Integer.MAX_VALUE
                ? String.valueOf(uuid.getLeastSignificantBits())
                : uuid.toString();
    }

    private UUID toUuid(Long rawId) {
        return rawId != null ? new UUID(0, rawId) : null;
    }

    private ProductInfo toProductInfo(ProductRow p) {
        return ProductInfo.builder()
                .id(toUuid(p.id())).restaurantId(toUuid(p.restaurantId())).name(p.name())
                .description(p.description()).image(p.image()).price(p.price())
                .available(p.isAvailable() != null && p.isAvailable())
                .build();
    }
}