package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.UserInfo;
import cl.flashdrop.orders.domain.port.UserPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalUserDto;
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
 * Cliente HTTP hacia Auth Service (contrato C-4).
 *
 * <p>Orders consume la API interna de Auth para obtener datos de usuario
 * en lugar de consultar directamente la tabla {@code users} de Supabase.</p>
 */
@Component
@RequiredArgsConstructor
public class AuthHttpClientAdapter implements UserPort {

    private static final Logger log = LoggerFactory.getLogger(AuthHttpClientAdapter.class);
    static final String SERVICE = "Auth";

    private final RestClient authInternalRestClient;

    @Override
    public Optional<UserInfo> findUserById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long id = IdConverter.toLong(userId);
        log.debug("Consultando usuario interno id={}", id);
        try {
            InternalUserDto dto = authInternalRestClient.get()
                    .uri("/api/internal/users/{id}", id)
                    .retrieve()
                    .body(InternalUserDto.class);
            if (dto == null) {
                return Optional.empty();
            }
            return Optional.of(UserInfo.builder()
                    .fullName(dto.fullName())
                    .email(dto.email())
                    .phone(dto.phone())
                    .build());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw InternalHttpSupport.httpError(SERVICE, e);
        } catch (ResourceAccessException e) {
            throw InternalHttpSupport.connectionFailure(SERVICE, e);
        }
    }
}
