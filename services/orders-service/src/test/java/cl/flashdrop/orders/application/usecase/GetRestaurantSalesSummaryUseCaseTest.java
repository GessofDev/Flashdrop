package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.dto.SalesSummary;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PR-orders-metrics (spec store-flow FR-1): resumen de ventas de un restaurante.
 * Solo pedidos ENTREGADO; ownership vía Catalog; rangos day/week/month calculados
 * server-side; top 5 productos por cantidad vendida.
 */
@ExtendWith(MockitoExtension.class)
class GetRestaurantSalesSummaryUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private CatalogPort catalogPort;

    private GetRestaurantSalesSummaryUseCase useCase;

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetRestaurantSalesSummaryUseCase(orderRepository, catalogPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenUserOwnsRestaurant() {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.of(RESTAURANT_ID));
    }

    private void givenDeliveredOrders(OffsetDateTime from, List<Order> orders) {
        when(orderRepository.findByRestaurantAndStatusAndCreatedAtBetween(
                RESTAURANT_ID, List.of(OrderStatus.ENTREGADO), from, NOW_UTC)).thenReturn(orders);
    }

    private static OrderItem item(UUID productId, String name, int quantity, long lineTotal) {
        return OrderItem.builder().productId(productId).productName(name).quantity(quantity)
                .lineTotal(BigDecimal.valueOf(lineTotal)).build();
    }

    private static Order order(long subtotal, OrderItem... items) {
        return Order.builder().id(UUID.randomUUID()).restaurantId(RESTAURANT_ID).status(OrderStatus.ENTREGADO)
                .subtotal(BigDecimal.valueOf(subtotal)).items(List.of(items)).build();
    }

    // --------------------------- rangos ---------------------------

    @Test
    void rangoWeek_consultaUltimos7Dias() {
        givenUserOwnsRestaurant();
        givenDeliveredOrders(NOW_UTC.minusDays(7), List.of());

        SalesSummary summary = useCase.execute(USER_ID, RESTAURANT_ID, "week");

        assertEquals("week", summary.range());
        assertEquals(NOW_UTC.minusDays(7), summary.from());
        assertEquals(NOW_UTC, summary.to());
    }

    @Test
    void rangoDay_consultaUltimas24Horas() {
        givenUserOwnsRestaurant();
        givenDeliveredOrders(NOW_UTC.minusHours(24), List.of());

        assertEquals(NOW_UTC.minusHours(24), useCase.execute(USER_ID, RESTAURANT_ID, "day").from());
    }

    @Test
    void rangoMonth_consultaUltimos30Dias() {
        givenUserOwnsRestaurant();
        givenDeliveredOrders(NOW_UTC.minusDays(30), List.of());

        assertEquals(NOW_UTC.minusDays(30), useCase.execute(USER_ID, RESTAURANT_ID, "month").from());
    }

    @Test
    void rangoInvalido_lanzaExcepcionSinConsultarNada() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, RESTAURANT_ID, "year"));

        assertEquals("Rango invalido: year. Valores validos: day, week, month", ex.getMessage());
        verify(catalogPort, never()).findRestaurantIdByUserId(any());
        verify(orderRepository, never()).findByRestaurantAndStatusAndCreatedAtBetween(any(), any(), any(), any());
    }

    // --------------------------- ownership ---------------------------

    @Test
    void restauranteDeOtroDueno_lanzaForbiddenSinConsultarVentas() {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.of(UUID.randomUUID()));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(USER_ID, RESTAURANT_ID, "week"));

        assertEquals("No puedes consultar las ventas de otro restaurante", ex.getMessage());
        verify(orderRepository, never()).findByRestaurantAndStatusAndCreatedAtBetween(any(), any(), any(), any());
    }

    @Test
    void usuarioSinRestaurante_lanzaForbidden() {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(USER_ID, RESTAURANT_ID, "week"));
    }

    // --------------------------- agregaciones ---------------------------

    @Test
    void sinVentas_devuelveTotalesEnCero() {
        givenUserOwnsRestaurant();
        givenDeliveredOrders(NOW_UTC.minusDays(7), List.of());

        SalesSummary summary = useCase.execute(USER_ID, RESTAURANT_ID, "week");

        assertEquals(RESTAURANT_ID, summary.restaurantId());
        assertEquals(0, summary.totalOrders());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalRevenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.averageTicket()));
        assertTrue(summary.topProducts().isEmpty());
    }

    /**
     * Ingreso = subtotal de productos (sin costo de despacho, que no es venta de la tienda);
     * ticket promedio redondeado a pesos (HALF_UP).
     */
    @Test
    void calculaTotalesYTicketPromedioSobreElSubtotal() {
        givenUserOwnsRestaurant();
        UUID burger = UUID.randomUUID();
        givenDeliveredOrders(NOW_UTC.minusDays(7), List.of(
                order(10000, item(burger, "Burger", 2, 10000)),
                order(5000, item(burger, "Burger", 1, 5000)),
                order(5001, item(burger, "Burger", 1, 5001))));

        SalesSummary summary = useCase.execute(USER_ID, RESTAURANT_ID, "week");

        assertEquals(3, summary.totalOrders());
        assertEquals(0, BigDecimal.valueOf(20001).compareTo(summary.totalRevenue()));
        assertEquals(0, BigDecimal.valueOf(6667).compareTo(summary.averageTicket())); // 20001 / 3 = 6667,0
    }

    @Test
    void topProductos_agrupaPorProducto_ordenaPorCantidad_yLimitaA5() {
        givenUserOwnsRestaurant();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(),
                p4 = UUID.randomUUID(), p5 = UUID.randomUUID(), p6 = UUID.randomUUID();
        givenDeliveredOrders(NOW_UTC.minusDays(7), List.of(
                order(0, item(p1, "Uno", 1, 1000), item(p2, "Dos", 6, 6000)),
                order(0, item(p1, "Uno", 2, 2000), item(p3, "Tres", 5, 5000)),
                order(0, item(p4, "Cuatro", 4, 4000), item(p5, "Cinco", 2, 2000), item(p6, "Seis", 1, 100))));

        List<SalesSummary.TopProduct> top = useCase.execute(USER_ID, RESTAURANT_ID, "week").topProducts();

        assertEquals(List.of(p2, p3, p4, p1, p5), top.stream().map(SalesSummary.TopProduct::productId).toList());
        SalesSummary.TopProduct uno = top.get(3);
        assertEquals("Uno", uno.productName());
        assertEquals(3, uno.quantitySold());
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(uno.revenue()));
    }

    /** Empate en cantidad: desempata el que más ingreso generó. */
    @Test
    void topProductos_conEmpateEnCantidad_ordenaPorIngreso() {
        givenUserOwnsRestaurant();
        UUID barato = UUID.randomUUID(), caro = UUID.randomUUID();
        givenDeliveredOrders(NOW_UTC.minusDays(7), List.of(
                order(0, item(barato, "Barato", 2, 1000), item(caro, "Caro", 2, 9000))));

        List<SalesSummary.TopProduct> top = useCase.execute(USER_ID, RESTAURANT_ID, "week").topProducts();

        assertEquals(List.of(caro, barato), top.stream().map(SalesSummary.TopProduct::productId).toList());
    }
}
