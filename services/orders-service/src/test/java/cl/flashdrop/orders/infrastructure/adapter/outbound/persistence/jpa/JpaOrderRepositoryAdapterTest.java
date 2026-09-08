package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderItemRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GAP-02 (auditoría 2026-09-04): cobertura real de {@link JpaOrderRepositoryAdapter} contra
 * PostgreSQL (Testcontainers + Flyway V1__init.sql) — el adapter que efectivamente usa el
 * perfil {@code postgres}/{@code default} en producción, antes sin ningún test dedicado.
 * El único test "E2E" existente ({@code OrdersE2ESimulatedTest}) ejercita el camino legacy
 * Supabase, no este.
 */
class JpaOrderRepositoryAdapterTest extends PostgresIntegrationTestSupport {

    @Autowired
    private SpringDataOrderRepository orderRepository;
    @Autowired
    private SpringDataOrderItemRepository orderItemRepository;
    @Autowired
    private SpringDataClientRepository clientRepository;

    private JpaOrderRepositoryAdapter adapter;
    private UUID clientId;
    private UUID restaurantId;

    @BeforeEach
    void setUp() {
        adapter = new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository);

        // orders.client_id tiene FK real hacia client(id) (V1__init.sql) — hace falta un
        // cliente persistido antes de poder guardar una orden, igual que en producción.
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()) // único por test, evita choques con la UNIQUE(user_id)
                .createdAt(OffsetDateTime.now())
                .build());
        clientId = IdConverter.toUuid(client.getId());
        restaurantId = IdConverter.toUuid(7L);
    }

    private Order.OrderBuilder baseOrder() {
        return Order.builder()
                .clientId(clientId)
                .restaurantId(restaurantId)
                .status(OrderStatus.NUEVO_PEDIDO)
                .address("Av. Providencia 1200")
                .subtotal(BigDecimal.valueOf(2000))
                .deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(4500))
                .paymentMethod(PaymentMethod.TARJETA);
    }

    private OrderItem sampleItem(long productId, int quantity, BigDecimal unitPrice) {
        return OrderItem.builder()
                .productId(IdConverter.toUuid(productId))
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    // ------------------------------------------------------------------
    // save / findById — persistencia real, incluida la relación order_items
    // ------------------------------------------------------------------

    @Test
    void save_shouldPersistOrderAndItemsAndReturnMappedOrderWithGeneratedId() {
        Order order = baseOrder()
                .items(List.of(sampleItem(101L, 2, BigDecimal.valueOf(1000))))
                .build();

        Order saved = adapter.save(order);

        assertNotNull(saved.getId());
        assertEquals(clientId, saved.getClientId());
        assertEquals(restaurantId, saved.getRestaurantId());
        assertEquals(OrderStatus.NUEVO_PEDIDO, saved.getStatus());
        assertEquals(0, BigDecimal.valueOf(4500).compareTo(saved.getTotal()));
        assertEquals(1, saved.getItems().size());
        assertEquals(IdConverter.toUuid(101L), saved.getItems().get(0).getProductId());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(saved.getItems().get(0).getLineTotal()));

        // La orden y sus items deben ser recuperables leyendo directo de la BD (no sólo
        // del objeto en memoria que devuelve save()).
        Optional<Order> reloaded = adapter.findById(saved.getId());
        assertTrue(reloaded.isPresent());
        assertEquals(1, reloaded.get().getItems().size());
    }

    @Test
    void save_shouldPersistMultipleItemsLinkedToTheSameOrder() {
        Order order = baseOrder()
                .items(List.of(
                        sampleItem(101L, 2, BigDecimal.valueOf(1000)),
                        sampleItem(102L, 1, BigDecimal.valueOf(500))
                ))
                .build();

        Order saved = adapter.save(order);

        assertEquals(2, saved.getItems().size());
    }

    @Test
    void findById_shouldReturnEmptyWhenOrderDoesNotExist() {
        assertTrue(adapter.findById(IdConverter.toUuid(999_999L)).isEmpty());
    }

    @Test
    void findById_shouldReturnEmptyForNullId() {
        assertTrue(adapter.findById(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // findAll — listado y filtro por restaurante
    // ------------------------------------------------------------------

    @Test
    void findAll_shouldFilterByRestaurantIdWhenProvided() {
        UUID otherRestaurant = IdConverter.toUuid(8L);
        adapter.save(baseOrder().items(List.of()).build());
        adapter.save(baseOrder().restaurantId(otherRestaurant).items(List.of()).build());

        List<Order> forRestaurant7 = adapter.findAll(restaurantId);

        assertEquals(1, forRestaurant7.size());
        assertEquals(restaurantId, forRestaurant7.get(0).getRestaurantId());
    }

    @Test
    void findAll_shouldReturnAllOrdersWhenRestaurantIdIsNull() {
        adapter.save(baseOrder().items(List.of()).build());
        adapter.save(baseOrder().restaurantId(IdConverter.toUuid(8L)).items(List.of()).build());

        assertEquals(2, adapter.findAll(null).size());
    }

    @Test
    void findAll_shouldReturnEmptyListWhenNoOrdersExist() {
        assertTrue(adapter.findAll(restaurantId).isEmpty());
    }

    // ------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------

    @Test
    void updateStatus_shouldPersistNewStatus() {
        Order saved = adapter.save(baseOrder().items(List.of()).build());

        adapter.updateStatus(saved.getId(), OrderStatus.PREPARANDO);

        Order reloaded = adapter.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.PREPARANDO, reloaded.getStatus());
    }

    @Test
    void updateStatus_shouldDoNothingWhenOrderDoesNotExist() {
        assertDoesNotThrow(() -> adapter.updateStatus(IdConverter.toUuid(999_999L), OrderStatus.PREPARANDO));
    }

    // ------------------------------------------------------------------
    // claimOrders / countActiveOrdersByDelivery / findByIdsForClaim / findByIds
    // ------------------------------------------------------------------

    @Test
    void claimOrders_shouldAssignDeliveryAndStatusToAllRequestedOrders() {
        Order order1 = adapter.save(baseOrder().status(OrderStatus.LISTO_PARA_RETIRO).items(List.of()).build());
        Order order2 = adapter.save(baseOrder().status(OrderStatus.LISTO_PARA_RETIRO).items(List.of()).build());
        UUID deliveryId = IdConverter.toUuid(9L);

        int updated = adapter.claimOrders(List.of(order1.getId(), order2.getId()), deliveryId, OrderStatus.EN_CAMINO);

        assertEquals(2, updated);
        assertEquals(deliveryId, adapter.findById(order1.getId()).orElseThrow().getDeliveryId());
        assertEquals(OrderStatus.EN_CAMINO, adapter.findById(order2.getId()).orElseThrow().getStatus());
    }

    @Test
    void countActiveOrdersByDelivery_shouldCountOnlyEnCaminoAndRetiradoStatuses() {
        UUID deliveryId = IdConverter.toUuid(9L);
        adapter.claimOrders(
                List.of(adapter.save(baseOrder().items(List.of()).build()).getId()),
                deliveryId, OrderStatus.EN_CAMINO);
        adapter.claimOrders(
                List.of(adapter.save(baseOrder().items(List.of()).build()).getId()),
                deliveryId, OrderStatus.RETIRADO);
        // Este no cuenta: distinto repartidor.
        adapter.claimOrders(
                List.of(adapter.save(baseOrder().items(List.of()).build()).getId()),
                IdConverter.toUuid(99L), OrderStatus.EN_CAMINO);

        assertEquals(2, adapter.countActiveOrdersByDelivery(deliveryId));
    }

    @Test
    void countActiveOrdersByDelivery_shouldReturnZeroWhenNoActiveOrders() {
        assertEquals(0, adapter.countActiveOrdersByDelivery(IdConverter.toUuid(9L)));
    }

    @Test
    void findByIds_shouldOmitNonExistentIdsAndKeepExistingOnes() {
        Order saved = adapter.save(baseOrder().items(List.of()).build());

        List<Order> result = adapter.findByIds(List.of(saved.getId(), IdConverter.toUuid(999_999L)));

        assertEquals(1, result.size());
        assertEquals(saved.getId(), result.get(0).getId());
    }

    @Test
    void findByIds_shouldReturnEmptyListForEmptyInput() {
        assertTrue(adapter.findByIds(List.of()).isEmpty());
        assertTrue(adapter.findByIds(null).isEmpty());
    }

    @Test
    void findByIdsForClaim_shouldBehaveLikeFindByIds() {
        Order saved = adapter.save(baseOrder().items(List.of()).build());

        List<Order> result = adapter.findByIdsForClaim(List.of(saved.getId()));

        assertEquals(1, result.size());
    }
}
