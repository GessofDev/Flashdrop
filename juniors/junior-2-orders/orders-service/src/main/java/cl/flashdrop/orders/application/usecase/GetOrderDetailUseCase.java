package cl.flashdrop.orders.application.usecase;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepositoryPort orderRepository;

    @Transactional(readOnly = true)
    public Order execute(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
    }
}
