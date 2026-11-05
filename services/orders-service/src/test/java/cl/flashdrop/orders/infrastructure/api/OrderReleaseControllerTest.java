package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ReleaseAssignedOrderUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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

@ExtendWith(MockitoExtension.class)
class OrderReleaseControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000065");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock
    private ReleaseAssignedOrderUseCase releaseUseCase;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderReleaseController controller = new OrderReleaseController(releaseUseCase, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void repartidorAutorizadoLiberaPedidoConExito() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        mockMvc.perform(post("/api/orders/{orderId}/release", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura de neumatico\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(releaseUseCase).execute(eq(ORDER_ID), eq(USER_ID), eq("Pinchadura de neumatico"), any());
    }

    @Test
    void usuarioSinRolRepartidorRecibeForbidden() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.CLIENTE));

        mockMvc.perform(post("/api/orders/{orderId}/release", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura de neumatico\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestSinMotivoDevuelveBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/release", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conflictoDeEstadoSeMapeaAConflict() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));
        doThrow(new OrderDomainException("Transicion de estado no permitida"))
                .when(releaseUseCase).execute(eq(ORDER_ID), eq(USER_ID), eq("Pinchadura"), any());

        mockMvc.perform(post("/api/orders/{orderId}/release", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura\"}"))
                .andExpect(status().isConflict());
    }
}
