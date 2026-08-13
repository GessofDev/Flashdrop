package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Información resumida de un repartidor.
 *
 * Pertenece conceptualmente a delivery-service, pero se gestiona localmente
 * en esta fase inicial a través del adaptador JPA.
 * Reemplazable en el futuro por una llamada a delivery-service sin impacto en el dominio.
 */
@Getter
@Builder
public class DeliveryInfo {

    private final UUID deliveryId;
    private final UUID userId;
    private final String name;
    private final String lastName;
    private final String phone;
    private final String vehicle;

    public String fullName() {
        String full = ((name != null ? name : "") + " " + (lastName != null ? lastName : "")).trim();
        return full.isBlank() ? "Repartidor" : full;
    }
}
