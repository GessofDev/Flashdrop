package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: Tomar Pedidos para Reparto (Claim).
 *
 * Permite a un repartidor seleccionar entre 1 y 3 pedidos del mismo restaurante
 * para iniciar su ruta. Aplica todas las validaciones de negocio necesarias.
 *
 * <p>PR-orders-claim (spec FR-3, ADR-1): el claim SOLO asigna el repartidor. Ya no muta
 * el estado del pedido a EN_CAMINO ni sincroniza la ruta: cada pedido conserva su estado
 * y el repartidor transiciona manualmente a RETIRADO al recogerlo
 * ({@code PUT /api/orders/{id}/status}). Un pedido "tomado" es uno con repartidor
 * asignado ({@link Order#isClaimable()}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimDeliveryOrdersUseCase {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;

    @Value("${orders.max-claim-per-route:3}")
    private int maxClaimPerRoute;

    /**
     * Resuelve el repartidor a partir de su {@code userId} de Auth (vía
     * {@link DeliveryPort#findDeliveryIdByUserId}) y ejecuta el claim.
     *
     * <p>Flujo canónico único: lo usan tanto el endpoint público legacy
     * ({@code POST /api/delivery/claim}) como el endpoint interno delegado por
     * Delivery Service ({@code POST /api/internal/orders/claim}) — Delivery manda
     * el {@code userId} crudo del subject del JWT, no un {@code delivery.id} ya
     * resuelto (verificado contra la implementación real de Delivery,
     * {@code HttpInternalOrdersClientAdapter}, commit {@code be86777}).</p>
     */
    @Transactional
    public void execute(UUID userId, List<UUID> orderIds) {
        UUID deliveryId = deliveryPort.findDeliveryIdByUserId(userId)
                .orElseThrow(() -> new OrderDomainException("El usuario no tiene perfil de repartidor"));

        claimForDelivery(deliveryId, orderIds);
    }

    private void claimForDelivery(UUID deliveryId, List<UUID> orderIds) {
        // 1. Validar cantidad de pedidos
        List<UUID> uniqueOrderIds = orderIds.stream().distinct().collect(Collectors.toList());
        if (uniqueOrderIds.isEmpty() || uniqueOrderIds.size() > maxClaimPerRoute) {
            throw new OrderDomainException(
                    "Debes seleccionar entre 1 y " + maxClaimPerRoute + " pedidos para tomar la ruta");
        }

        // 2. Verificar que el repartidor no tiene pedidos activos en ruta
        int activeOrders = orderRepository.countActiveOrdersByDelivery(deliveryId);
        if (activeOrders > 0) {
            throw new OrderDomainException(
                    "Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos");
        }

        // 3. Verificar que todos los pedidos existen y pueden ser tomados
        List<Order> orders = orderRepository.findByIdsForClaim(uniqueOrderIds);
        if (orders.size() != uniqueOrderIds.size()) {
            throw new OrderDomainException("Uno o mas pedidos ya no estan disponibles");
        }

        // 4. Verificar que ninguno ha sido ya tomado (tiene repartidor o estado cerrado)
        boolean alreadyTaken = orders.stream().anyMatch(o -> !o.isClaimable());
        if (alreadyTaken) {
            throw new OrderDomainException("Uno o mas pedidos ya fueron tomados por otro repartidor");
        }

        // 5. Verificar que todos son del mismo restaurante
        Set<UUID> restaurants = orders.stream()
                .map(Order::getRestaurantId)
                .collect(Collectors.toSet());
        if (restaurants.size() > 1) {
            throw new OrderDomainException("Solo puedes agrupar pedidos del mismo restaurante");
        }

        // 6. Asignar repartidor, sin tocar el estado de los pedidos
        int updated = orderRepository.claimOrders(uniqueOrderIds, deliveryId);
        if (updated != uniqueOrderIds.size()) {
            throw new OrderDomainException(
                    "Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista");
        }


        log.info("Repartidor {} tomó {} pedidos: {}", deliveryId, uniqueOrderIds.size(), uniqueOrderIds);
    }
}
