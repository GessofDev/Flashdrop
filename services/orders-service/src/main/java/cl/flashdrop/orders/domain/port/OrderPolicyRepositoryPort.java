package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.OrderPolicy;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para las politicas de plazos del pedido. */
public interface OrderPolicyRepositoryPort {

    /** La politica global; vacia solo si alguien borro la fila que crea la migracion V9. */
    Optional<OrderPolicy> findGlobal();

    Optional<OrderPolicy> findByRestaurantId(UUID restaurantId);

    /** Crea o reemplaza la politica (global si {@code restaurantId} es nulo). */
    OrderPolicy save(OrderPolicy policy, UUID updatedByUserId);
}
