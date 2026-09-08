package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: Listar Pedidos.
 *
 * Cuando se recibe un userId (dueño de restaurante), filtra por el restaurante
 * correspondiente. Sin userId, retorna todos los pedidos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListOrdersUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final OrderEnricher enricher;

    @Transactional(readOnly = true)
    public List<Order> execute(UUID userId) {
        UUID restaurantId = null;

        if (userId != null) {
            restaurantId = catalogPort.findRestaurantIdByUserId(userId).orElse(null);
            if (restaurantId == null) {
                log.debug("Usuario {} no tiene restaurante asociado", userId);
                return List.of();
            }
        }

        return orderRepository.findAll(restaurantId).stream()
                .peek(enricher::enrich)
                .collect(Collectors.toList());
    }
}
