package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: Listar Pedidos Disponibles para Reparto (PR-orders-available, spec FR-2).
 *
 * Devuelve hasta {@code limit} pedidos de un restaurante que un repartidor puede tomar:
 * en LISTO_PARA_RETIRO y sin repartidor asignado, en orden de llegada (FIFO).
 * El chequeo del rol Repartidor se hace en el controller, igual que el resto de los
 * chequeos de identidad del JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListAvailableOrdersUseCase {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 50;

    private final OrderRepositoryPort orderRepository;
    private final OrderEnricher enricher;

    @Transactional(readOnly = true)
    public List<Order> execute(UUID restaurantId, int limit) {
        if (restaurantId == null) {
            throw new OrderDomainException("El restaurante es obligatorio");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new OrderDomainException("El limite debe estar entre " + MIN_LIMIT + " y " + MAX_LIMIT);
        }

        List<Order> orders = orderRepository.findAvailableForDelivery(restaurantId, limit);
        orders.forEach(enricher::enrich);
        log.debug("Pedidos disponibles para reparto en restaurante {}: {}", restaurantId, orders.size());
        return orders;
    }
}
