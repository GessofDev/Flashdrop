package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.UserInfo;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.UserPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.persistence.dto.ClientRow;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador REST a Supabase (PostgREST) para la tabla PROPIA de Orders: {@code client}.
 *
 * <p>La tabla {@code client} pertenece a Orders, por lo que su acceso directo es permitido.
 * Para enriquecer el nombre del cliente se consulta Auth (C-4) a través de {@link UserPort},
 * evitando así el acceso directo a la tabla {@code users}.</p>
 */
@Component
@RequiredArgsConstructor
public class SupabaseRestClientAdapter implements ClientPort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRestClientAdapter.class);
    static final String TABLE = "client";

    private final RestClient supabaseRestClient;
    private final UserPort userPort;

    @Override
    public Optional<UUID> findClientIdByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long rawUserId = IdConverter.toLong(userId);
        log.debug("Consultando cliente interno por userId={}", rawUserId);
        try {
            ClientRow[] rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + TABLE)
                            .queryParam("user_id", "eq." + rawUserId)
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .body(ClientRow[].class);
            if (rows == null || rows.length == 0) {
                return Optional.empty();
            }
            return Optional.of(IdConverter.toUuid(rows[0].id()));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw cl.flashdrop.orders.infrastructure.adapter.outbound.http.InternalHttpSupport
                    .httpError("Client", e);
        } catch (ResourceAccessException e) {
            throw cl.flashdrop.orders.infrastructure.adapter.outbound.http.InternalHttpSupport
                    .connectionFailure("Client", e);
        }
    }

    @Override
    public Optional<ClientInfo> findClientById(UUID clientId) {
        if (clientId == null) {
            return Optional.empty();
        }
        long rawId = IdConverter.toLong(clientId);
        log.debug("Consultando cliente interno id={}", rawId);
        try {
            ClientRow[] rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + TABLE)
                            .queryParam("id", "eq." + rawId)
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .body(ClientRow[].class);
            if (rows == null || rows.length == 0) {
                return Optional.empty();
            }
            ClientRow row = rows[0];
            Optional<UserInfo> user = userPort.findUserById(IdConverter.toUuid(row.userId()));
            return Optional.of(ClientInfo.builder()
                    .clientId(clientId)
                    .fullName(user.map(UserInfo::getFullName).orElse("Cliente"))
                    .email(user.map(UserInfo::getEmail).orElse(null))
                    .phone(user.map(UserInfo::getPhone).orElse(null))
                    .build());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw cl.flashdrop.orders.infrastructure.adapter.outbound.http.InternalHttpSupport
                    .httpError("Client", e);
        } catch (ResourceAccessException e) {
            throw cl.flashdrop.orders.infrastructure.adapter.outbound.http.InternalHttpSupport
                    .connectionFailure("Client", e);
        }
    }
}
