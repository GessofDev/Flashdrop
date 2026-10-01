package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderDomainTest {

    @Test
    void shouldValidateSingleRestaurantSuccessfully() {
        UUID restId = UUID.randomUUID();
        List<ProductInfo> products = List.of(
                ProductInfo.builder().id(UUID.randomUUID()).restaurantId(restId).price(BigDecimal.valueOf(1000)).build(),
                ProductInfo.builder().id(UUID.randomUUID()).restaurantId(restId).price(BigDecimal.valueOf(2000)).build()
        );

        assertDoesNotThrow(() -> Order.validateSingleRestaurant(products));
    }

    @Test
    void shouldThrowExceptionWhenProductsFromMultipleRestaurants() {
        List<ProductInfo> products = List.of(
                ProductInfo.builder().id(UUID.randomUUID()).restaurantId(UUID.randomUUID()).price(BigDecimal.valueOf(1000)).build(),
                ProductInfo.builder().id(UUID.randomUUID()).restaurantId(UUID.randomUUID()).price(BigDecimal.valueOf(2000)).build()
        );

        OrderDomainException exception = assertThrows(OrderDomainException.class,
                () -> Order.validateSingleRestaurant(products));
        assertEquals("El pedido debe contener productos de un mismo local", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenProductListIsEmpty() {
        assertThrows(OrderDomainException.class,
                () -> Order.validateSingleRestaurant(Collections.emptyList()));
    }

    @Test
    void shouldCalculateSubtotalCorrectly() {
        List<OrderItem> items = List.of(
                OrderItem.builder().productId(UUID.randomUUID()).quantity(2).unitPrice(BigDecimal.valueOf(1500))
                        .lineTotal(BigDecimal.valueOf(3000)).build(),
                OrderItem.builder().productId(UUID.randomUUID()).quantity(1).unitPrice(BigDecimal.valueOf(2500))
                        .lineTotal(BigDecimal.valueOf(2500)).build()
        );

        BigDecimal subtotal = Order.calculateSubtotal(items);
        assertEquals(0, BigDecimal.valueOf(5500).compareTo(subtotal));
    }

    @Test
    void shouldCalculateTotalCorrectly() {
        BigDecimal subtotal = BigDecimal.valueOf(5000);
        BigDecimal deliveryFee = BigDecimal.valueOf(2500);

        BigDecimal total = Order.calculateTotal(subtotal, deliveryFee);
        assertEquals(0, BigDecimal.valueOf(7500).compareTo(total));
    }

    @Test
    void shouldGenerateUniqueDisplayCodeFromPersistedId() {
        Order order = Order.builder()
                .id(new UUID(0L, 10L))
                .build();

        assertEquals("FD-10", order.code());
    }

    @Test
    void shouldThrowExceptionOnStatusTransitionFromDelivered() {
        Order order = Order.builder()
                .status(OrderStatus.ENTREGADO)
                .build();

        assertThrows(OrderDomainException.class,
                () -> order.validateStatusTransition(OrderStatus.NUEVO_PEDIDO));
    }

    // PR-orders-claim: el claim ya no muta el estado, así que "tomado" se define por tener
    // repartidor asignado, no por estar en EN_CAMINO/RETIRADO.

    @Test
    void shouldBeClaimableWhenReadyAndWithoutDelivery() {
        Order order = Order.builder()
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .build();

        assertTrue(order.isClaimable());
    }

    @Test
    void shouldNotBeClaimableWhenAlreadyAssignedToADelivery() {
        Order order = Order.builder()
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .deliveryId(UUID.randomUUID())
                .build();

        assertFalse(order.isClaimable());
    }

    @Test
    void shouldNotBeClaimableWhenStatusIsClosed() {
        Order order = Order.builder()
                .status(OrderStatus.ENTREGADO)
                .build();

        assertFalse(order.isClaimable());
    }
}
