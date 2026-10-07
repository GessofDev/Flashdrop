package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR-orders-status-authz (spec FR-4, NFR-2): matriz rol × transición de
 * {@code PUT /api/orders/{id}/status} a nivel HTTP, con el controller y el use case
 * reales — solo los puertos de salida (repositorio, Catalog, Delivery, eventos) son mocks.
 *
 * <p>Antes de este PR cualquier JWT válido podía cambiar el estado de cualquier pedido
 * (sin rol, sin ownership — riesgo residual documentado en GAP-04) y la única transición
 * rechazada era ENTREGADO → *. Códigos esperados: 403 rol / 403 ownership (restaurante
 * dueño, repartidor asignado) / 409 estado.</p>
 *
 * <p>Prueba de integración liviana (use case real), fuera de las pruebas unitarias del plan:
 * la de §3.2 es {@code OrderControllerStatusAuthzTest} (use case simulado). Ojo: el pom no
 * configura Failsafe, así que Surefire no ejecuta clases {@code *IT}.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerStatusAuthzIT {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock private ListOrdersUseCase listOrdersUseCase;
    @Mock private ListAvailableOrdersUseCase listAvailableOrdersUseCase;
    @Mock private OrderRepositoryPort orderRepository;
    @Mock private DeliveryPort deliveryPort;
    @Mock private EventPublisherPort eventPublisher;
    @Mock private CatalogPort catalogPort;

    private MockMvc mockMvc;

    private static final long USER_ID = 42L;
    private static final UUID ORDER_ID = IdConverter.toUuid(501L);
    private static final UUID RESTAURANT_ID = IdConverter.toUuid(7L);
    private static final UUID DELIVERY_ID = IdConverter.toUuid(9L);

    @BeforeEach
    void setUp() {
        UpdateOrderStatusUseCase updateOrderStatusUseCase =
                new UpdateOrderStatusUseCase(orderRepository, deliveryPort, eventPublisher, catalogPort);
        ReflectionTestUtils.setField(updateOrderStatusUseCase, "statusUpdatedRoutingKey", "order.status.updated");

        OrderController controller = new OrderController(createOrderUseCase, getOrderDetailUseCase,
                listOrdersUseCase, updateOrderStatusUseCase, new CurrentUserResolver(), listAvailableOrdersUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Simula lo que deja JwtValidationFilter: principal = sub, authorities = ROLE_<rol>. */
    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private void pedidoEnEstado(OrderStatus status) {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(
                Order.builder().id(ORDER_ID).restaurantId(RESTAURANT_ID)
                        .deliveryId(DELIVERY_ID).status(status).build()));
    }

    private void usuarioEsRepartidor(UUID deliveryId) {
        when(deliveryPort.findDeliveryIdByUserId(IdConverter.toUuid(USER_ID)))
                .thenReturn(Optional.of(deliveryId));
    }

    private void usuarioEsDuenoDe(UUID restaurantId) {
        when(catalogPort.findRestaurantIdByUserId(IdConverter.toUuid(USER_ID)))
                .thenReturn(Optional.of(restaurantId));
    }

    private ResultActions cambiarEstadoA(String nuevoEstado) throws Exception {
        return mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + nuevoEstado + "\"}"));
    }

    // --------------------------- 200 ---------------------------

    @Test
    void repartidor_retiraPedidoListo_200() throws Exception {
        autenticadoComo("Repartidor");
        pedidoEnEstado(OrderStatus.LISTO_PARA_RETIRO);
        usuarioEsRepartidor(DELIVERY_ID);

        cambiarEstadoA("Retirado").andExpect(status().isOk());

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.RETIRADO);
    }

    @Test
    void restauranteDueno_empiezaAPreparar_200() throws Exception {
        autenticadoComo("Restaurante");
        pedidoEnEstado(OrderStatus.NUEVO_PEDIDO);
        usuarioEsDuenoDe(RESTAURANT_ID);

        cambiarEstadoA("Preparando").andExpect(status().isOk());

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.PREPARANDO);
    }

    // --------------------------- 403 rol ---------------------------

    @Test
    void repartidor_noPuedePonerPreparando_403() throws Exception {
        autenticadoComo("Repartidor");
        pedidoEnEstado(OrderStatus.NUEVO_PEDIDO);

        cambiarEstadoA("Preparando")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void restaurante_noPuedeRetirarPedido_403() throws Exception {
        autenticadoComo("Restaurante");
        pedidoEnEstado(OrderStatus.LISTO_PARA_RETIRO);

        cambiarEstadoA("Retirado").andExpect(status().isForbidden());

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void cliente_noPuedeCambiarEstado_403() throws Exception {
        autenticadoComo("Cliente");
        pedidoEnEstado(OrderStatus.RETIRADO);

        cambiarEstadoA("Entregado").andExpect(status().isForbidden());

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    // --------------------------- 403 ownership (IDOR) ---------------------------

    @Test
    void restauranteDeOtroLocal_noPuedeCambiarElPedido_403() throws Exception {
        autenticadoComo("Restaurante");
        pedidoEnEstado(OrderStatus.NUEVO_PEDIDO);
        usuarioEsDuenoDe(IdConverter.toUuid(99L));

        cambiarEstadoA("Preparando")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No puedes modificar pedidos de otro restaurante"));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    /** Acuerdo con delivery-service: el repartidor solo cambia pedidos asignados a él. */
    @Test
    void repartidorNoAsignadoAlPedido_403() throws Exception {
        autenticadoComo("Repartidor");
        pedidoEnEstado(OrderStatus.LISTO_PARA_RETIRO);
        usuarioEsRepartidor(IdConverter.toUuid(99L));

        cambiarEstadoA("Retirado")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No puedes modificar pedidos asignados a otro repartidor"));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    // --------------------------- 409 transición ---------------------------

    @Test
    void repartidor_noPuedeRetirarPedidoYaEntregado_409() throws Exception {
        autenticadoComo("Repartidor");
        pedidoEnEstado(OrderStatus.ENTREGADO);
        usuarioEsRepartidor(DELIVERY_ID);

        cambiarEstadoA("Retirado")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void repartidor_noPuedeEntregarSinRetirar_409() throws Exception {
        autenticadoComo("Repartidor");
        pedidoEnEstado(OrderStatus.LISTO_PARA_RETIRO);
        usuarioEsRepartidor(DELIVERY_ID);

        cambiarEstadoA("Entregado").andExpect(status().isConflict());

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void restauranteDueno_noPuedeVolverAPreparandoDesdeEntregado_409() throws Exception {
        autenticadoComo("Restaurante");
        pedidoEnEstado(OrderStatus.ENTREGADO);
        usuarioEsDuenoDe(RESTAURANT_ID);

        cambiarEstadoA("Preparando").andExpect(status().isConflict());

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    // --------------------------- 400 / 404 ---------------------------

    @Test
    void estadoNoReconocido_400() throws Exception {
        autenticadoComo("Restaurante");

        cambiarEstadoA("Cancelado").andExpect(status().isBadRequest());
    }

    @Test
    void pedidoInexistente_404() throws Exception {
        autenticadoComo("Repartidor");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        cambiarEstadoA("Retirado").andExpect(status().isNotFound());
    }
}
