package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.GetRestaurantSalesSummaryUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR-orders-metrics (spec store-flow FR-1, tasks T-27/T-27b):
 * {@code GET /api/orders/restaurants/{id}/sales-summary} a nivel HTTP, con controller y use
 * case reales — solo el repositorio y Catalog son mocks; reloj fijo.
 *
 * <p>Nombre {@code *Test} (no {@code *IT}): el pom no configura Failsafe.</p>
 */
@ExtendWith(MockitoExtension.class)
class SalesSummaryControllerTest {

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private CatalogPort catalogPort;

    private MockMvc mockMvc;

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);
    private static final long USER_ID = 2L;
    private static final UUID RESTAURANT_ID = IdConverter.toUuid(7L);
    private static final UUID PRODUCT_ID = IdConverter.toUuid(101L);

    @BeforeEach
    void setUp() {
        GetRestaurantSalesSummaryUseCase useCase = new GetRestaurantSalesSummaryUseCase(
                orderRepository, catalogPort, Clock.fixed(NOW, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new SalesSummaryController(useCase, new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private void usuarioEsDuenoDe(UUID restaurantId) {
        when(catalogPort.findRestaurantIdByUserId(IdConverter.toUuid(USER_ID))).thenReturn(Optional.of(restaurantId));
    }

    private void ventasEntregadas(OffsetDateTime from, List<Order> orders) {
        when(orderRepository.findByRestaurantAndStatusAndCreatedAtBetween(
                RESTAURANT_ID, List.of(OrderStatus.ENTREGADO), from, NOW_UTC)).thenReturn(orders);
    }

    private static Order pedidoEntregado(long subtotal, int quantity) {
        return Order.builder().id(UUID.randomUUID()).restaurantId(RESTAURANT_ID).status(OrderStatus.ENTREGADO)
                .subtotal(BigDecimal.valueOf(subtotal))
                .items(List.of(OrderItem.builder().productId(PRODUCT_ID).productName("Burger")
                        .quantity(quantity).lineTotal(BigDecimal.valueOf(subtotal)).build()))
                .build();
    }

    @Test
    void restauranteDueno_recibeResumenSemanalPorDefecto_200() throws Exception {
        autenticadoComo("Restaurante");
        usuarioEsDuenoDe(RESTAURANT_ID);
        ventasEntregadas(NOW_UTC.minusDays(7), List.of(pedidoEntregado(10000, 2), pedidoEntregado(5000, 1)));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value(7))
                .andExpect(jsonPath("$.data.range").value("week"))
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.totalRevenue").value(15000))
                .andExpect(jsonPath("$.data.averageTicket").value(7500))
                .andExpect(jsonPath("$.data.topProducts[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.topProducts[0].productName").value("Burger"))
                .andExpect(jsonPath("$.data.topProducts[0].quantitySold").value(3))
                .andExpect(jsonPath("$.data.topProducts[0].revenue").value(15000));
    }

    @Test
    void rangoDay_consultaLasUltimas24Horas() throws Exception {
        autenticadoComo("Restaurante");
        usuarioEsDuenoDe(RESTAURANT_ID);
        ventasEntregadas(NOW_UTC.minusHours(24), List.of());

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7).param("range", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("day"))
                .andExpect(jsonPath("$.data.totalOrders").value(0));
    }

    /** Usuario multirol (admin@demo.cl): basta con que tenga el rol Restaurante. */
    @Test
    void usuarioMultirolConRolRestaurante_puedeConsultar() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        usuarioEsDuenoDe(RESTAURANT_ID);
        ventasEntregadas(NOW_UTC.minusDays(7), List.of());

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isOk());
    }

    @Test
    void sinRolRestaurante_403() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(catalogPort, never()).findRestaurantIdByUserId(any());
    }

    /** IDOR: una tienda no puede ver las ventas de otra. */
    @Test
    void restauranteDeOtroLocal_403() throws Exception {
        autenticadoComo("Restaurante");
        usuarioEsDuenoDe(IdConverter.toUuid(99L));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No puedes consultar las ventas de otro restaurante"));

        verify(orderRepository, never()).findByRestaurantAndStatusAndCreatedAtBetween(any(), any(), any(), any());
    }

    @Test
    void rangoInvalido_400() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7).param("range", "year"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rango invalido: year. Valores validos: day, week, month"));
    }

    @Test
    void idNoNumerico_400() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", "abc"))
                .andExpect(status().isBadRequest());
    }
}
