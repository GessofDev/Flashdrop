package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.application.usecase.VerifyDeliveryCodeUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.model.ReceptionMode;
import cl.flashdrop.orders.domain.model.Role;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/orders/{id}} acepta el id numérico (el que emiten Delivery y Catalog, convención
 * "wire = Long, dominio = UUID") y el UUID legado, igual que {@code PUT /status} y
 * {@code POST /deliver}. Así el cliente puede consultar su código de entrega con el mismo id con
 * el que opera el resto del flujo.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerDetailIdFormatTest {

    private static final long USER_ID = 42L;
    private static final UUID USER_UUID = IdConverter.toUuid(USER_ID);
    private static final UUID ORDER_ID = IdConverter.toUuid(501L);

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock private ListOrdersUseCase listOrdersUseCase;
    @Mock private ListAvailableOrdersUseCase listAvailableOrdersUseCase;
    @Mock private UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @Mock private VerifyDeliveryCodeUseCase verifyDeliveryCodeUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(createOrderUseCase, getOrderDetailUseCase,
                listOrdersUseCase, updateOrderStatusUseCase, verifyDeliveryCodeUseCase,
                new CurrentUserResolver(), listAvailableOrdersUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null, List.of(new SimpleGrantedAuthority("ROLE_Cliente"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void givenOrder() {
        Order order = Order.builder().id(ORDER_ID).status(OrderStatus.RETIRADO)
                .paymentMethod(PaymentMethod.fromValue("Tarjeta")).subtotal(BigDecimal.TEN)
                .deliveryFee(BigDecimal.ONE).total(BigDecimal.TEN).items(List.of())
                .receptionMode(ReceptionMode.SEGURO).deliveryCode("482913").build();
        when(getOrderDetailUseCase.execute(eq(ORDER_ID), any(), eq(USER_UUID))).thenReturn(order);
    }

    @Test
    void idNumerico_seResuelveAlMismoPedidoYMuestraElCodigoAlDueno() throws Exception {
        givenOrder();

        mockMvc.perform(get("/api/orders/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryCode").value("482913"))
                .andExpect(jsonPath("$.data.receptionMode").value("SEGURO"));

        verify(getOrderDetailUseCase).execute(ORDER_ID, Set.of(Role.CLIENTE), USER_UUID);
    }

    @Test
    void uuidLegado_sigueFuncionando() throws Exception {
        givenOrder();

        mockMvc.perform(get("/api/orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryCode").value("482913"));
    }

    @Test
    void idInvalido_respondeBadRequestSinLlegarAlCasoDeUso() throws Exception {
        mockMvc.perform(get("/api/orders/abc"))
                .andExpect(status().isBadRequest());

        verify(getOrderDetailUseCase, never()).execute(any(), any(), any());
    }
}
