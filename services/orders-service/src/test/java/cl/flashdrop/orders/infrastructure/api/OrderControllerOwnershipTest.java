package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Mis pedidos" y detalle: el controller toma la identidad y los roles del token (nunca del
 * parámetro {@code user_id}) y delega en el caso de uso, cuyo 403 traduce el handler.
 * Slice web con los casos de uso simulados.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerOwnershipTest {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock private ListOrdersUseCase listOrdersUseCase;
    @Mock private ListAvailableOrdersUseCase listAvailableOrdersUseCase;
    @Mock private UpdateOrderStatusUseCase updateOrderStatusUseCase;

    private MockMvc mockMvc;

    private static final long USER_ID = 42L;
    private static final UUID USER_UUID = IdConverter.toUuid(USER_ID);
    private static final UUID ORDER_ID = IdConverter.toUuid(501L);

    @BeforeEach
    void setUp() {
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

    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    @Test
    void misPedidos_sinUserId_usaLaIdentidadYLosRolesDelToken() throws Exception {
        autenticadoComo("Cliente");
        when(listOrdersUseCase.execute(USER_UUID, Set.of(Role.CLIENTE))).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")).andExpect(status().isOk());

        verify(listOrdersUseCase).execute(USER_UUID, Set.of(Role.CLIENTE));
    }

    @Test
    void misPedidos_conUserIdDeOtro_403_yNoConsultaNada() throws Exception {
        autenticadoComo("Cliente");

        mockMvc.perform(get("/api/orders").param("user_id", "999"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listOrdersUseCase);
    }

    @Test
    void detalle_entregaAlCasoDeUsoLosRolesYElUsuarioDelToken() throws Exception {
        autenticadoComo("Restaurante");
        when(getOrderDetailUseCase.execute(ORDER_ID, Set.of(Role.RESTAURANTE), USER_UUID))
                .thenThrow(new ForbiddenOperationException("No puedes consultar este pedido"));

        mockMvc.perform(get("/api/orders/{id}", ORDER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("No puedes consultar este pedido"));

        verify(getOrderDetailUseCase).execute(ORDER_ID, Set.of(Role.RESTAURANTE), USER_UUID);
    }
}
