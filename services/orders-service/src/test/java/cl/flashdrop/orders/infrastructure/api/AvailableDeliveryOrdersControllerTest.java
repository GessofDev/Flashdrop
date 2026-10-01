package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR-orders-available (spec FR-2): {@code GET /api/orders/available-for-delivery} a nivel
 * HTTP, con controller y use case reales (solo el repositorio y el enricher son mocks).
 * El filtrado por estado, por repartidor asignado y el orden FIFO se prueban contra
 * Postgres real en {@code JpaOrderRepositoryAdapterTest}.
 *
 * <p>Nombre {@code *Test} (no {@code *IT}): el pom no configura Failsafe.</p>
 */
@ExtendWith(MockitoExtension.class)
class AvailableDeliveryOrdersControllerTest {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock private ListOrdersUseCase listOrdersUseCase;
    @Mock private UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @Mock private OrderRepositoryPort orderRepository;
    @Mock private OrderEnricher enricher;

    private MockMvc mockMvc;

    private static final UUID RESTAURANT_ID = IdConverter.toUuid(7L);

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(createOrderUseCase, getOrderDetailUseCase,
                listOrdersUseCase, updateOrderStatusUseCase, new CurrentUserResolver(),
                new ListAvailableOrdersUseCase(orderRepository, enricher));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", null, Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static Order pedidoListo(long id) {
        return Order.builder()
                .id(IdConverter.toUuid(id))
                .restaurantId(RESTAURANT_ID)
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .address("Av. Providencia 1200")
                .subtotal(BigDecimal.valueOf(2000))
                .deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(4500))
                .paymentMethod(PaymentMethod.TARJETA)
                .createdAt(OffsetDateTime.now())
                .items(List.of())
                .build();
    }

    @Test
    void repartidor_recibePedidosDisponiblesEnOrden_conLimitePorDefecto5() throws Exception {
        autenticadoComo("Repartidor");
        when(orderRepository.findAvailableForDelivery(RESTAURANT_ID, 5))
                .thenReturn(List.of(pedidoListo(501L), pedidoListo(502L)));

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(IdConverter.toUuid(501L).toString()))
                .andExpect(jsonPath("$.data[1].id").value(IdConverter.toUuid(502L).toString()))
                .andExpect(jsonPath("$.data[0].status").value("Listo para retiro"));
    }

    /** Wire Long → dominio UUID en el borde (mismo criterio que {@code GET /api/orders?user_id=}). */
    @Test
    void conviertaRestaurantIdLongAUuidYRespetaElLimitePedido() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery")
                        .param("restaurant_id", "7").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(orderRepository).findAvailableForDelivery(RESTAURANT_ID, 3);
    }

    /** Usuario multirol (admin@demo.cl): basta con que tenga el rol Repartidor. */
    @Test
    void usuarioMultirolConRolRepartidor_puedeConsultar() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void sinRolRepartidor_403() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }

    @Test
    void faltaRestaurantId_400() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parametro obligatorio: restaurant_id"));

        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }

    @Test
    void limiteMayorA50_400() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery")
                        .param("restaurant_id", "7").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El limite debe estar entre 1 y 50"));

        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }

    @Test
    void limiteCero_400() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery")
                        .param("restaurant_id", "7").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restaurantIdNoNumerico_400() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "abc"))
                .andExpect(status().isBadRequest());
    }
}
