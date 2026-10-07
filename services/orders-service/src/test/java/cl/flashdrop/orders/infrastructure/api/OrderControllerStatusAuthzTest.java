package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
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

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan de pruebas §3.2: {@code PUT /api/orders/{id}/status} — matriz de códigos HTTP según
 * los roles del token, 200 ante transición autorizada y 403/409 delegados a los handlers.
 *
 * <p>Slice web con {@code MockMvc} standalone y {@link UpdateOrderStatusUseCase}
 * <b>simulado</b> (Mockito): la decisión de rol/ownership/transición pertenece al use case
 * (probado en {@code UpdateOrderStatusUseCaseTest} y {@code RoleTransitionPolicyTest}); acá
 * se comprueba que el controller entregue al use case los roles y el usuario del JWT, y que
 * {@link GlobalExceptionHandler} traduzca cada excepción al código HTTP correcto. La versión
 * con el use case real es {@code OrderControllerStatusAuthzIT}.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerStatusAuthzTest {

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

    /** Simula lo que deja JwtValidationFilter: principal = sub, authorities = ROLE_<rol>. */
    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private ResultActions cambiarEstadoA(String nuevoEstado) throws Exception {
        return mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + nuevoEstado + "\"}"));
    }

    // --------------------------- 200 ---------------------------

    @Test
    void repartidor_transicionAutorizada_200_yElUseCaseRecibeRolYUsuarioDelToken() throws Exception {
        autenticadoComo("Repartidor");

        cambiarEstadoA("Retirado").andExpect(status().isOk());

        verify(updateOrderStatusUseCase).execute(ORDER_ID, "Retirado", Set.of(Role.REPARTIDOR), USER_UUID);
    }

    @Test
    void restaurante_transicionAutorizada_200_yElUseCaseRecibeRolYUsuarioDelToken() throws Exception {
        autenticadoComo("Restaurante");

        cambiarEstadoA("Preparando").andExpect(status().isOk());

        verify(updateOrderStatusUseCase).execute(ORDER_ID, "Preparando", Set.of(Role.RESTAURANTE), USER_UUID);
    }

    @Test
    void usuarioConVariosRoles_elUseCaseRecibeTodosLosRolesDelToken() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");

        cambiarEstadoA("Preparando").andExpect(status().isOk());

        verify(updateOrderStatusUseCase).execute(ORDER_ID, "Preparando",
                Set.of(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR), USER_UUID);
    }

    // --------------------------- 403 (delegado al handler) ---------------------------

    @Test
    void cuandoElUseCaseRechazaElRol_403_conCuerpoDeError() throws Exception {
        autenticadoComo("Cliente");
        doThrow(new StatusTransitionForbiddenException("El rol no puede fijar el estado Entregado"))
                .when(updateOrderStatusUseCase).execute(any(), any(), any(), any());

        cambiarEstadoA("Entregado")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("El rol no puede fijar el estado Entregado"));
    }

    @Test
    void cuandoElUseCaseRechazaElOwnership_403() throws Exception {
        autenticadoComo("Restaurante");
        doThrow(new StatusTransitionForbiddenException("No puedes modificar pedidos de otro restaurante"))
                .when(updateOrderStatusUseCase).execute(any(), any(), any(), any());

        cambiarEstadoA("Preparando")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("No puedes modificar pedidos de otro restaurante"));
    }

    // --------------------------- 409 / 404 / 400 (delegados al handler) ---------------------------

    @Test
    void cuandoLaTransicionNoEsValida_409() throws Exception {
        autenticadoComo("Repartidor");
        doThrow(new OrderDomainException("Transicion de estado no permitida: ENTREGADO -> RETIRADO"))
                .when(updateOrderStatusUseCase).execute(any(), any(), any(), any());

        cambiarEstadoA("Retirado")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void cuandoElPedidoNoExiste_404() throws Exception {
        autenticadoComo("Repartidor");
        doThrow(new OrderDomainException("Pedido no encontrado: " + ORDER_ID))
                .when(updateOrderStatusUseCase).execute(any(), any(), any(), any());

        cambiarEstadoA("Retirado")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void cuandoElEstadoNoEsReconocido_400() throws Exception {
        autenticadoComo("Restaurante");
        doThrow(new OrderDomainException("Estado no valido: Cancelado"))
                .when(updateOrderStatusUseCase).execute(any(), any(), any(), any());

        cambiarEstadoA("Cancelado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void cuerpoSinStatus_400_yNoLlegaAlUseCase() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(updateOrderStatusUseCase, never()).execute(any(), any(), any(), any());
    }
}
