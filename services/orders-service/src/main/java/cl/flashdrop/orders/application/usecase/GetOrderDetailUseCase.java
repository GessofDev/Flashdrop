package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: Obtener Detalle de un Pedido.
 *
 * Enriquece el pedido con información referencial de otros servicios (cliente,
 * restaurante) a través de los ports de dominio, manteniendo el repositorio
 * limitado a sus tablas propias.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderEnricher enricher;

    @Transactional(readOnly = true)
    public Order execute(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        enricher.enrich(order);
        log.debug("Detalle de pedido {} enriquecido", orderId);
        return order;
    }
}
