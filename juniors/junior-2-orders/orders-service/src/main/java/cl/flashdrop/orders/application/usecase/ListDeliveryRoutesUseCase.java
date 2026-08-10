package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: Listar Rutas de Reparto.
 *
 * Retorna todas las rutas de entrega con la información del pedido asociado,
 * ordenadas por distancia ascendente para facilitar la selección del repartidor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListDeliveryRoutesUseCase {

    private final OrderRepositoryPort orderRepository;

    @Transactional(readOnly = true)
    public List<Order> execute() {
        return orderRepository.findAllRoutesWithOrders();
    }
}
