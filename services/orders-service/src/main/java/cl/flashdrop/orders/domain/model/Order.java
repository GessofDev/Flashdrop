package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

    private final List<OrderItem> items;
    private DeliveryRoute route;

    private ClientInfo clientInfo;
    private RestaurantInfo restaurantInfo;
    private DeliveryInfo deliveryInfo;

    // -------------------------------------------------------
    // Computed / Display
    // -------------------------------------------------------

    /**
     * Código de pedido en formato FD-[short-uuid] para mostrar al usuario.
     */
    public String code() {
        if (id == null) return "FD-NEW";
        String uuidStr = id.toString();
        return "FD-" + uuidStr.substring(0, Math.min(uuidStr.length(), 8)).toUpperCase();
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
     * Valida que la transición de estado sea permitida.
     * Aplica las reglas de flujo definidas para el negocio.
     *
     * @param newStatus el nuevo estado solicitado
     * @throws OrderDomainException si la transición no es válida
     */
    public void validateStatusTransition(OrderStatus newStatus) {
        // Pedidos entregados no pueden cambiar de estado
        if (this.status == OrderStatus.ENTREGADO) {
            throw new OrderDomainException(
                    "El pedido ya fue entregado y no puede modificar su estado");
        }
        // Pedidos cancelados (no existe en el sistema, pero si se agrega en el futuro)
        // se podrá validar aquí sin modificar los casos de uso
    }

    /**
     * Asigna el delivery y actualiza el estado del pedido al ser tomado por un repartidor.
     */
    public void assignDelivery(UUID deliveryId) {
        if (this.status.isClosed()) {
            throw new OrderDomainException(
                    "No se puede asignar repartidor a un pedido que ya fue tomado o entregado");
        }
        this.deliveryId = deliveryId;
        this.status = OrderStatus.EN_CAMINO;
    }

    /**
     * Indica si el pedido puede ser tomado por un repartidor.
     */
    public boolean isClaimable() {
        return !this.status.isClosed();
    }
}
