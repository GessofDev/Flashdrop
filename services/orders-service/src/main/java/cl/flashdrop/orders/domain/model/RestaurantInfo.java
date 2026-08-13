package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Información resumida de un restaurante.
 *
 * Pertenece conceptualmente a catalog-service, pero se gestiona localmente
 * en esta fase inicial a través del adaptador JPA.
 */
@Getter
@Builder
public class RestaurantInfo {

    private final UUID restaurantId;
    private final String name;
    private final String address;
}
