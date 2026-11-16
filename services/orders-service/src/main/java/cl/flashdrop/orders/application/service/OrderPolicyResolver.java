package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.port.OrderPolicyRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Entrega los plazos que aplican a una tienda: lo que la tienda configuro y, donde no
 * configuro nada, el valor global del administrador.
 */
@Slf4j
@Service
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class OrderPolicyResolver {

    /** Ultimo recurso si la fila global no existe (no deberia pasar: la crea V9). */
    static final OrderPolicy.Effective FALLBACK = new OrderPolicy.Effective(15, 10, 5);

    private final OrderPolicyRepositoryPort policies;

    @Transactional(readOnly = true)
    public OrderPolicy.Effective effectiveFor(UUID restaurantId) {
        OrderPolicy global = policies.findGlobal().orElse(null);
        if (global == null) {
            log.warn("No existe la politica global de pedidos; se usan los valores por defecto");
            return FALLBACK;
        }
        OrderPolicy store = restaurantId == null ? null : policies.findByRestaurantId(restaurantId).orElse(null);
        return OrderPolicy.resolve(global, store);
    }
}
