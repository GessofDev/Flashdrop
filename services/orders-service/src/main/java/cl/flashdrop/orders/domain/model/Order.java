package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entidad principal del dominio de Pedidos.
 *
 * Encapsula el estado del pedido y las reglas de negocio que gobiernan su ciclo de vida.
 * Es completamente independiente de Spring y JPA: sin anotaciones de framework.
 */
@Getter
@Setter
@Builder
public class Order {

    private final UUID id;
    private final UUID clientId;
    private final UUID restaurantId;
    private UUID deliveryId;

    private OrderStatus status;
    private final String address;
    private final BigDecimal subtotal;
    private final BigDecimal deliveryFee;
    private final BigDecimal total;
    private final PaymentMethod paymentMethod;
    private final OffsetDateTime createdAt;

    private List<OrderItem> items;
    private DeliveryRoute route;

    private ClientInfo clientInfo;
    private RestaurantInfo restaurantInfo;
    private DeliveryInfo deliveryInfo;

    // -------------------------------------------------------
    // Computed / Display
    // -------------------------------------------------------

    /** Código de pedido basado en el ID persistido para mostrar al usuario. */
    public String code() {
        if (id == null) return "FD-NEW";
        long persistedId = id.getLeastSignificantBits() & Long.MAX_VALUE;
        return String.format("FD-%02d", persistedId);
    }

    // -------------------------------------------------------
    // Business Rules
    // -------------------------------------------------------

    /**
     * Valida que todos los ítems pertenezcan al mismo restaurante.
     *
     * @param productInfos mapa de productId → ProductInfo con el restaurantId
     * @throws OrderDomainException si los productos son de más de un restaurante
     */
    public static void validateSingleRestaurant(List<ProductInfo> productInfos) {
        if (productInfos == null || productInfos.isEmpty()) {
            throw new OrderDomainException("El pedido debe contener al menos un producto");
        }
        UUID firstRestaurantId = productInfos.get(0).getRestaurantId();
        boolean hasMultiple = productInfos.stream()
                .anyMatch(p -> !p.getRestaurantId().equals(firstRestaurantId));
        if (hasMultiple) {
            throw new OrderDomainException("El pedido debe contener productos de un mismo local");
        }
    }

    /**
     * Calcula el subtotal sumando los totales de cada línea de ítem.
     */
    public static BigDecimal calculateSubtotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula el total del pedido: subtotal + delivery fee.
     */
    public static BigDecimal calculateTotal(BigDecimal subtotal, BigDecimal deliveryFee) {
        return subtotal.add(deliveryFee);
    }

    /**
     * Transiciones de estado permitidas (spec FR-4, tasks T-11). ENTREGADO es terminal.
     * EN_CAMINO es legacy (deprecated): el claim ya no lo asigna, pero los pedidos que
     * quedaron en ese estado deben poder terminar su ciclo.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.NUEVO_PEDIDO, EnumSet.of(OrderStatus.PREPARANDO, OrderStatus.LISTO_PARA_RETIRO),
            OrderStatus.PREPARANDO, EnumSet.of(OrderStatus.LISTO_PARA_RETIRO),
            OrderStatus.LISTO_PARA_RETIRO, EnumSet.of(OrderStatus.RETIRADO, OrderStatus.EN_CAMINO),
            OrderStatus.RETIRADO, EnumSet.of(OrderStatus.ENTREGADO, OrderStatus.EN_CAMINO),
            OrderStatus.EN_CAMINO, EnumSet.of(OrderStatus.RETIRADO, OrderStatus.ENTREGADO),
            OrderStatus.ENTREGADO, EnumSet.noneOf(OrderStatus.class)
    );

    /**
     * Valida que la transición de estado sea permitida según {@link #VALID_TRANSITIONS}.
     *
     * @param newStatus el nuevo estado solicitado
     * @throws OrderDomainException si la transición no es válida (se mapea a 409 CONFLICT)
     */
    public void validateStatusTransition(OrderStatus newStatus) {
        if (!VALID_TRANSITIONS.getOrDefault(this.status, Set.of()).contains(newStatus)) {
            throw new OrderDomainException("Transicion de estado no permitida: "
                    + this.status.getValue() + " -> " + newStatus.getValue());
        }
    }

    /**
     * Indica si el pedido puede ser tomado por un repartidor.
     *
     * <p>PR-orders-claim (spec FR-3): el claim ya no muta el estado a EN_CAMINO, así que un
     * pedido tomado sigue en LISTO_PARA_RETIRO — lo que lo marca como tomado es tener
     * repartidor asignado. Se mantiene además el chequeo por estado para los pedidos legacy
     * que ya están en EN_CAMINO/RETIRADO/ENTREGADO.</p>
     */
    public boolean isClaimable() {
        return this.deliveryId == null && !this.status.isClosed();
    }
}
