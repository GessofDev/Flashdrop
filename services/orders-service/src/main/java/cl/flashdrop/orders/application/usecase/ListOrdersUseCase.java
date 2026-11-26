package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: Listar "Mis pedidos" del usuario autenticado.
 *
 * <p>Cada usuario ve solo lo suyo, según los roles de su token: un Restaurante ve los pedidos
 * de su restaurante y un Cliente ve los pedidos que él hizo. Un usuario con ambos roles ve la
 * unión. Un usuario sin restaurante ni perfil de cliente recibe una lista vacía; nunca se
 * devuelven pedidos ajenos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListOrdersUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final ClientPort clientPort;
    private final OrderEnricher enricher;

    @Transactional(readOnly = true)
    public List<Order> execute(UUID currentUserId, Set<Role> currentRoles) {
        return execute(currentUserId, currentRoles, null);
    }

    @Transactional(readOnly = true)
    public List<Order> execute(UUID currentUserId, Set<Role> currentRoles, UUID requestedRestaurantId) {
        Map<UUID, Order> visible = new LinkedHashMap<>();

        if (currentRoles.contains(Role.RESTAURANTE)) {
            if (requestedRestaurantId != null) {
                if (!catalogPort.checkRestaurantMembership(currentUserId, requestedRestaurantId)) {
                    throw new ForbiddenOperationException("No puedes consultar pedidos de otro restaurante");
                }
                orderRepository.findVisibleToStore(requestedRestaurantId)
                        .forEach(o -> visible.putIfAbsent(o.getId(), o));
            } else {
                List<UUID> restaurantIds = catalogPort.findOperableRestaurantIdsByUserId(currentUserId);
                if (restaurantIds.isEmpty()) {
                    log.debug("Usuario {} no tiene restaurante asociado", currentUserId);
                } else {
                    for (UUID restaurantId : restaurantIds) {
                        orderRepository.findVisibleToStore(restaurantId)
                                .forEach(o -> visible.putIfAbsent(o.getId(), o));
                    }
                }
            }
        }

        if (currentRoles.contains(Role.CLIENTE)) {
            clientPort.findClientIdByUserId(currentUserId).ifPresentOrElse(
                    clientId -> orderRepository.findByClientId(clientId)
                            .forEach(o -> visible.putIfAbsent(o.getId(), o)),
                    () -> log.debug("Usuario {} no tiene perfil de cliente", currentUserId));
        }

        visible.values().forEach(enricher::enrich);
        return List.copyOf(visible.values());
    }
}
