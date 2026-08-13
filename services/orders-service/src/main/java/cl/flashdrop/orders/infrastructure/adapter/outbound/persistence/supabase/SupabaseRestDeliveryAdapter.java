package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.DeliveryInfo;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.infrastructure.persistence.dto.ClientRow;
import cl.flashdrop.orders.infrastructure.persistence.dto.DeliveryRow;
import cl.flashdrop.orders.infrastructure.persistence.dto.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SupabaseRestDeliveryAdapter implements DeliveryPort {

    private final RestClient supabaseRestClient;

    @Override
    public Optional<UUID> findClientIdByUserId(UUID userId) {
        if (userId != null) {
            ClientRow[] rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/client").queryParam("user_id", "eq." + extractRawId(userId))
                            .queryParam("select", "id").build())
                    .retrieve().body(ClientRow[].class);
            if (rows != null && rows.length > 0) return Optional.of(toUuid(rows[0].id()));
        }
        ClientRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/client").queryParam("select", "id")
                        .queryParam("order", "id.asc").queryParam("limit", "1").build())
                .retrieve().body(ClientRow[].class);
        if (rows != null && rows.length > 0) return Optional.of(toUuid(rows[0].id()));
        return Optional.empty();
    }

    @Override
    public Optional<ClientInfo> findClientById(UUID clientId) {
        ClientRow[] clientRows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/client").queryParam("id", "eq." + extractRawId(clientId))
                        .queryParam("select", "*").build())
                .retrieve().body(ClientRow[].class);
        if (clientRows == null || clientRows.length == 0) return Optional.empty();
        ClientRow c = clientRows[0];
        UserRow user = null;
        if (c.userId() != null) {
            UserRow[] userRows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users").queryParam("id", "eq." + c.userId())
                            .queryParam("select", "*").build())
                    .retrieve().body(UserRow[].class);
            if (userRows != null && userRows.length > 0) user = userRows[0];
        }
        return Optional.of(ClientInfo.builder()
                .clientId(toUuid(c.id())).userId(toUuid(c.userId()))
                .name(user != null ? user.name() : null)
                .lastName(user != null ? user.lastName() : null)
                .email(user != null ? user.email() : null)
                .phone(user != null ? user.phone() : null).build());
    }

    @Override
    public Optional<UUID> findDeliveryIdByUserId(UUID userId) {
        DeliveryRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/delivery").queryParam("user_id", "eq." + extractRawId(userId))
                        .queryParam("select", "id").build())
                .retrieve().body(DeliveryRow[].class);
        if (rows != null && rows.length > 0) return Optional.of(toUuid(rows[0].id()));
        return Optional.empty();
    }

    @Override
    public Optional<DeliveryInfo> findDeliveryById(UUID deliveryId) {
        DeliveryRow[] deliveryRows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/delivery").queryParam("id", "eq." + extractRawId(deliveryId))
                        .queryParam("select", "*").build())
                .retrieve().body(DeliveryRow[].class);
        if (deliveryRows == null || deliveryRows.length == 0) return Optional.empty();
        DeliveryRow d = deliveryRows[0];
        UserRow user = null;
        if (d.userId() != null) {
            UserRow[] userRows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users").queryParam("id", "eq." + d.userId())
                            .queryParam("select", "*").build())
                    .retrieve().body(UserRow[].class);
            if (userRows != null && userRows.length > 0) user = userRows[0];
        }
        return Optional.of(DeliveryInfo.builder()
                .deliveryId(toUuid(d.id())).userId(toUuid(d.userId()))
                .name(user != null ? user.name() : null)
                .lastName(user != null ? user.lastName() : null)
                .phone(user != null ? user.phone() : null)
                .vehicle(d.vehicle()).build());
    }

    private String extractRawId(UUID uuid) {
        return uuid.getLeastSignificantBits() <= Integer.MAX_VALUE
                ? String.valueOf(uuid.getLeastSignificantBits())
                : uuid.toString();
    }

    private UUID toUuid(Long rawId) {
        return rawId != null ? new UUID(0, rawId) : null;
    }
}