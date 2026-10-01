package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.application.dto.SalesSummary;
import cl.flashdrop.orders.application.usecase.GetRestaurantSalesSummaryUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderItemRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PR-orders-metrics (tasks T-23 y T-28): resumen de ventas contra PostgreSQL real
 * (Testcontainers + Flyway V1__init.sql), con {@link GetRestaurantSalesSummaryUseCase} real
 * sobre {@link JpaOrderRepositoryAdapter}. Solo Catalog (ownership) es mock.
 *
 * <p>Dataset de 10 pedidos: 4 ENTREGADO en la última semana, 1 ENTREGADO hace 8 días
 * (fuera de "week", dentro de "month"), 4 en otros estados y 1 de otro restaurante.</p>
 *
 * <p>Vive en este paquete porque {@link PostgresIntegrationTestSupport} es package-private.</p>
 */
class SalesSummaryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private SpringDataOrderRepository orderRepository;
    @Autowired
    private SpringDataOrderItemRepository orderItemRepository;
    @Autowired
    private SpringDataClientRepository clientRepository;

    private JpaOrderRepositoryAdapter adapter;
    private GetRestaurantSalesSummaryUseCase useCase;

    private static final UUID USER_ID = IdConverter.toUuid(2L);
    private static final UUID PRODUCT_A = IdConverter.toUuid(101L);
    private static final UUID PRODUCT_B = IdConverter.toUuid(102L);
    private static final UUID PRODUCT_C = IdConverter.toUuid(103L);

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final OffsetDateTime t = now.atOffset(ZoneOffset.UTC);
    private UUID clientId;
    private UUID restaurantId;

    @BeforeEach
    void setUp() {
        adapter = new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository);
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime())
                .createdAt(OffsetDateTime.now())
                .build());
        clientId = IdConverter.toUuid(client.getId());
        // Restaurante propio por test: el contenedor Postgres es compartido entre clases.
        restaurantId = IdConverter.toUuid(2_000_000L + (System.nanoTime() % 1_000_000L));

        CatalogPort catalogPort = mock(CatalogPort.class);
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.of(restaurantId));
        useCase = new GetRestaurantSalesSummaryUseCase(adapter, catalogPort, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static OrderItem item(UUID productId, String name, int quantity, long lineTotal) {
        return OrderItem.builder().productId(productId).productName(name).quantity(quantity)
                .unitPrice(BigDecimal.valueOf(lineTotal / quantity)).lineTotal(BigDecimal.valueOf(lineTotal)).build();
    }

    private void save(UUID restId, OrderStatus status, OffsetDateTime createdAt, long subtotal, OrderItem... items) {
        adapter.save(Order.builder()
                .clientId(clientId).restaurantId(restId).status(status)
                .address("Av. Providencia 1200")
                .subtotal(BigDecimal.valueOf(subtotal)).deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(subtotal + 2500))
                .paymentMethod(PaymentMethod.TARJETA)
                .createdAt(createdAt)
                .items(List.of(items))
                .build());
    }

    private void givenDataset() {
        // 4 ENTREGADO en la última semana
        save(restaurantId, OrderStatus.ENTREGADO, t.minusHours(25), 7000,
                item(PRODUCT_A, "A", 2, 4000), item(PRODUCT_B, "B", 1, 3000));
        save(restaurantId, OrderStatus.ENTREGADO, t.minusDays(2), 2000, item(PRODUCT_A, "A", 1, 2000));
        save(restaurantId, OrderStatus.ENTREGADO, t.minusDays(6), 4500, item(PRODUCT_C, "C", 3, 4500));
        save(restaurantId, OrderStatus.ENTREGADO, t.minusHours(23), 6000, item(PRODUCT_B, "B", 2, 6000));
        // ENTREGADO hace 8 días: fuera de "week", dentro de "month"
        save(restaurantId, OrderStatus.ENTREGADO, t.minusDays(8), 1000, item(PRODUCT_A, "A", 1, 1000));
        // Otros estados dentro del rango: no son ventas cerradas
        save(restaurantId, OrderStatus.NUEVO_PEDIDO, t.minusHours(1), 9000, item(PRODUCT_A, "A", 9, 9000));
        save(restaurantId, OrderStatus.PREPARANDO, t.minusHours(2), 9000, item(PRODUCT_A, "A", 9, 9000));
        save(restaurantId, OrderStatus.LISTO_PARA_RETIRO, t.minusHours(3), 9000, item(PRODUCT_A, "A", 9, 9000));
        save(restaurantId, OrderStatus.RETIRADO, t.minusHours(4), 9000, item(PRODUCT_A, "A", 9, 9000));
        // Otro restaurante
        save(IdConverter.toUuid(3_999_999L), OrderStatus.ENTREGADO, t.minusHours(5), 9000,
                item(PRODUCT_A, "A", 9, 9000));
    }

    @Test
    void repository_shouldFilterByRestaurantStatusAndDateRangeAndLoadItems() {
        givenDataset();

        List<Order> result = adapter.findByRestaurantAndStatusAndCreatedAtBetween(
                restaurantId, List.of(OrderStatus.ENTREGADO), t.minusDays(7), t);

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(o -> o.getStatus() == OrderStatus.ENTREGADO));
        assertTrue(result.stream().allMatch(o -> restaurantId.equals(o.getRestaurantId())));
        assertEquals(5, result.stream().mapToInt(o -> o.getItems().size()).sum());
    }

    @Test
    void week_shouldAggregateOnlyDeliveredOrdersOfTheLastSevenDays() {
        givenDataset();

        SalesSummary summary = useCase.execute(USER_ID, restaurantId, "week");

        assertEquals(4, summary.totalOrders());
        assertEquals(0, BigDecimal.valueOf(19500).compareTo(summary.totalRevenue()));
        assertEquals(0, BigDecimal.valueOf(4875).compareTo(summary.averageTicket()));
        // A, B y C venden 3 unidades cada uno: desempata el ingreso (B 9000 > A 6000 > C 4500).
        assertEquals(List.of(PRODUCT_B, PRODUCT_A, PRODUCT_C),
                summary.topProducts().stream().map(SalesSummary.TopProduct::productId).toList());
        SalesSummary.TopProduct b = summary.topProducts().get(0);
        assertEquals("B", b.productName());
        assertEquals(3, b.quantitySold());
        assertEquals(0, BigDecimal.valueOf(9000).compareTo(b.revenue()));
    }

    @Test
    void day_shouldOnlyIncludeTheLast24Hours() {
        givenDataset();

        SalesSummary summary = useCase.execute(USER_ID, restaurantId, "day");

        assertEquals(1, summary.totalOrders());
        assertEquals(0, BigDecimal.valueOf(6000).compareTo(summary.totalRevenue()));
    }

    @Test
    void month_shouldIncludeOlderDeliveredOrders() {
        givenDataset();

        SalesSummary summary = useCase.execute(USER_ID, restaurantId, "month");

        assertEquals(5, summary.totalOrders());
        assertEquals(0, BigDecimal.valueOf(20500).compareTo(summary.totalRevenue()));
    }

    @Test
    void withoutDeliveredOrders_shouldReturnZeroTotals() {
        SalesSummary summary = useCase.execute(USER_ID, restaurantId, "week");

        assertEquals(0, summary.totalOrders());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalRevenue()));
        assertTrue(summary.topProducts().isEmpty());
    }
}
