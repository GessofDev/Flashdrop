package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Información resumida de un cliente asociado a un pedido.
 *
 * Pertenece conceptualmente a auth-service, pero se gestiona localmente
 * en esta fase inicial a través del adaptador {@code DeliveryJpaAdapter}.
 * Reemplazable en el futuro por una llamada a auth-service sin impacto en el dominio.
 */
@Getter
@Builder
public class ClientInfo {

    private final UUID clientId;
    private final UUID userId;
    private final String name;
    private final String lastName;
    private final String email;
    private final String phone;

    public String fullName() {
        String full = ((name != null ? name : "") + " " + (lastName != null ? lastName : "")).trim();
        return full.isBlank() ? "Cliente" : full;
    }
}
