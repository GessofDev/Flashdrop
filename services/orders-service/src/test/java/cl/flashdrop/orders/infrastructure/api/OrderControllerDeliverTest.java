package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.dto.DeliveryCodeVerification;
import cl.flashdrop.orders.application.dto.DeliveryCodeVerification.Outcome;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.application.usecase.VerifyDeliveryCodeUseCase;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.exception.StatusTransitionForbiddenException;
import cl.flashdrop.orders.domain.model.Role;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/orders/{id}/deliver}: traduce el resultado del caso de uso al código HTTP
 * (200 entregado, 422 código incorrecto con intentos restantes, 423 bloqueado) y valida el body.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerDeliverTest {

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
                String.valueOf(USER_ID), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_Repartidor"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions deliver(String body) throws Exception {
        return mockMvc.perform(post("/api/orders/501/deliver")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void codigoCorrecto_responde200YEntregaAlCasoDeUsoLosDatosDelToken() throws Exception {
        when(verifyDeliveryCodeUseCase.execute(eq(ORDER_ID), eq("482913"), any(), eq(USER_UUID)))
                .thenReturn(new DeliveryCodeVerification(Outcome.DELIVERED, 3));

        deliver("{\"code\":\"482913\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(verifyDeliveryCodeUseCase).execute(ORDER_ID, "482913", Set.of(Role.REPARTIDOR), USER_UUID);
    }

    @Test
    void codigoIncorrecto_responde422ConLosIntentosRestantes() throws Exception {
        when(verifyDeliveryCodeUseCase.execute(any(), any(), any(), any()))
                .thenReturn(new DeliveryCodeVerification(Outcome.WRONG_CODE, 2));

        deliver("{\"code\":\"111111\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.remainingAttempts").value(2))
                .andExpect(jsonPath("$.codeRenewed").value(false));
    }

    @Test
    void intentosAgotados_responde422IndicandoQueSeRenovoElCodigo() throws Exception {
        when(verifyDeliveryCodeUseCase.execute(any(), any(), any(), any()))
                .thenReturn(new DeliveryCodeVerification(Outcome.CODE_RENEWED, 3));

        deliver("{\"code\":\"111111\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.remainingAttempts").value(3))
                .andExpect(jsonPath("$.codeRenewed").value(true));
    }

    @Test
    void pedidoBloqueado_responde423() throws Exception {
        when(verifyDeliveryCodeUseCase.execute(any(), any(), any(), any()))
                .thenReturn(new DeliveryCodeVerification(Outcome.LOCKED, 0));

        deliver("{\"code\":\"111111\"}")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("LOCKED"))
                .andExpect(jsonPath("$.remainingAttempts").value(0));
    }

    @Test
    void repartidorAjeno_responde403() throws Exception {
        doThrow(new StatusTransitionForbiddenException("No puedes modificar pedidos asignados a otro repartidor"))
                .when(verifyDeliveryCodeUseCase).execute(any(), any(), any(), any());

        deliver("{\"code\":\"111111\"}").andExpect(status().isForbidden());
    }

    @Test
    void pedidoNoRetirado_responde409() throws Exception {
        doThrow(new OrderDomainException("Transicion de estado no permitida: Listo para retiro -> Entregado"))
                .when(verifyDeliveryCodeUseCase).execute(any(), any(), any(), any());

        deliver("{\"code\":\"111111\"}").andExpect(status().isConflict());
    }

    @Test
    void codigoConFormatoInvalido_responde400SinLlegarAlCasoDeUso() throws Exception {
        deliver("{\"code\":\"12ab\"}").andExpect(status().isBadRequest());
        deliver("{}").andExpect(status().isBadRequest());
    }
}
