package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.RouteNotFoundException;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SupabaseRestRouteRepository implements RouteRepository {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRestRouteRepository.class);
    private static final String TABLE = "delivery_routes";

    private final RestClient supabase;

    public SupabaseRestRouteRepository(RestClient supabaseRestClient) {
        this.supabase = supabaseRestClient;
    }

    @Override
    public Optional<DeliveryRoute> findById(Long id) {
        DeliveryRouteRow[] rows = supabase.get()
                .uri("/{table}?id=eq.{id}&limit=1", TABLE, id)
                .retrieve()
                .body(DeliveryRouteRow[].class);
        if (rows == null || rows.length == 0) {
            return Optional.empty();
        }
        return Optional.of(toDomain(rows[0]));
    }

    @Override
    public Optional<DeliveryRoute> findByOrderId(Long orderId) {
        DeliveryRouteRow[] rows = supabase.get()
                .uri("/{table}?order_id=eq.{orderId}&limit=1", TABLE, orderId)
                .retrieve()
                .body(DeliveryRouteRow[].class);
        if (rows == null || rows.length == 0) {
            return Optional.empty();
        }
        return Optional.of(toDomain(rows[0]));
    }

    @Override
    public List<DeliveryRoute> findAll() {
        DeliveryRouteRow[] rows = supabase.get()
                .uri("/{table}?select=*&order=created_at.desc", TABLE)
                .retrieve()
                .body(DeliveryRouteRow[].class);
        if (rows == null || rows.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(rows).map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return findByOrderId(orderId).isPresent();
    }

    @Override
    public DeliveryRoute save(DeliveryRoute route) {
        DeliveryRouteRow body = new DeliveryRouteRow(
                route.getId(),
                route.getOrderId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm() != null ? route.getDistanceKm().value() : null,
                route.getEstimatedMinutes() != null ? route.getEstimatedMinutes().minutes() : null,
                route.getStatus() != null ? route.getStatus().getDbValue() : null,
                route.getCreatedAt() != null ? route.getCreatedAt() : Instant.now()
        );
        DeliveryRouteRow[] rows = supabase.post()
                .uri("/{table}", TABLE)
                .header("Prefer", "return=representation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Supabase POST {} failed: {} {}", TABLE, res.getStatusCode(), new String(res.getBody().readAllBytes()));
                })
                .body(DeliveryRouteRow[].class);
        if (rows == null || rows.length == 0) {
            throw new IllegalStateException("Supabase did not return the saved delivery route row");
        }
        return toDomain(rows[0]);
    }

    @Override
    public DeliveryRoute updateStatus(Long id, String status) {
        Map<String, Object> patch = Map.of("status", status);
        DeliveryRouteRow[] rows = supabase.patch()
                .uri("/{table}?id=eq.{id}", TABLE, id)
                .header("Prefer", "return=representation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(patch)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Supabase PATCH {} id={} failed: {} {}", TABLE, id, res.getStatusCode(), new String(res.getBody().readAllBytes()));
                })
                .body(DeliveryRouteRow[].class);
        if (rows == null || rows.length == 0) {
            throw new RouteNotFoundException(id);
        }
        return toDomain(rows[0]);
    }

    private DeliveryRoute toDomain(DeliveryRouteRow row) {
        return new DeliveryRoute(
                row.id(),
                row.orderId(),
                row.pickupAddress(),
                row.deliveryAddress(),
                row.distanceKm() != null ? Distance.of(row.distanceKm()) : Distance.zero(),
                row.estimatedMinutes() != null ? EstimatedTime.of(row.estimatedMinutes()) : EstimatedTime.zero(),
                RouteStatus.fromDbValue(row.status()),
                row.createdAt()
        );
    }
}