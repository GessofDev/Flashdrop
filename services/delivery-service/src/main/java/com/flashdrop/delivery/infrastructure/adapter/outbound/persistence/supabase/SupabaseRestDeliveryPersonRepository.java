package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class SupabaseRestDeliveryPersonRepository implements DeliveryPersonRepository {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRestDeliveryPersonRepository.class);
    private static final String TABLE = "delivery";

    private final RestClient supabase;

    public SupabaseRestDeliveryPersonRepository(RestClient supabaseRestClient) {
        this.supabase = supabaseRestClient;
    }

    @Override
    public Optional<DeliveryPerson> findById(Long id) {
        DeliveryRow[] rows = supabase.get()
                .uri("/{table}?id=eq.{id}&limit=1", TABLE, id)
                .retrieve()
                .body(DeliveryRow[].class);
        if (rows == null || rows.length == 0) {
            return Optional.empty();
        }
        return Optional.of(toDomain(rows[0]));
    }

    @Override
    public Optional<DeliveryPerson> findByUserId(Long userId) {
        DeliveryRow[] rows = supabase.get()
                .uri("/{table}?user_id=eq.{userId}&limit=1", TABLE, userId)
                .retrieve()
                .body(DeliveryRow[].class);
        if (rows == null || rows.length == 0) {
            return Optional.empty();
        }
        return Optional.of(toDomain(rows[0]));
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return findByUserId(userId).isPresent();
    }

    @Override
    public DeliveryPerson save(DeliveryPerson deliveryPerson) {
        DeliveryRow body = new DeliveryRow(
                deliveryPerson.getId(),
                deliveryPerson.getUserId(),
                deliveryPerson.getVehicle() != null ? deliveryPerson.getVehicle().getDbValue() : null,
                deliveryPerson.getCreatedAt() != null ? deliveryPerson.getCreatedAt() : Instant.now()
        );
        DeliveryRow[] rows = supabase.post()
                .uri("/{table}", TABLE)
                .header("Prefer", "return=representation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Supabase POST {} failed: {} {}", TABLE, res.getStatusCode(), new String(res.getBody().readAllBytes()));
                })
                .body(DeliveryRow[].class);
        if (rows == null || rows.length == 0) {
            throw new IllegalStateException("Supabase did not return the saved delivery person row");
        }
        return toDomain(rows[0]);
    }

    private DeliveryPerson toDomain(DeliveryRow row) {
        return new DeliveryPerson(
                row.id(),
                row.userId(),
                VehicleType.fromDbValue(row.vehicle()),
                row.createdAt()
        );
    }
}