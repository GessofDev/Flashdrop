package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.GetDeliveryEventsUseCase;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryEventsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetDeliveryEventsUseCase getDeliveryEventsUseCase;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private OrderDeliveryEventsController controller;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Soporte consulta eventos de entrega exitosamente")
    void soporteConsultaEventosExitosamente() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userId);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.SOPORTE));

        DeliveryOperation op = DeliveryOperation.pending(
                orderId, DeliveryOperationType.RELEASE, userId, Role.REPARTIDOR,
                "Motivo", null, deliveryId, OrderStatus.LISTO_PARA_RETIRO, "corr-1", OffsetDateTime.now()
        ).succeed(null, OrderStatus.LISTO_PARA_RETIRO, OffsetDateTime.now()).withId(5L);

        when(getDeliveryEventsUseCase.execute(orderId)).thenReturn(List.of(op));

        mockMvc.perform(get("/api/orders/{orderId}/delivery-events", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].operationType").value("RELEASE"))
                .andExpect(jsonPath("$.data[0].state").value("SUCCEEDED"));

        verify(getDeliveryEventsUseCase).execute(orderId);
    }

    @Test
    @DisplayName("Usuario no soporte (ej: Repartidor) recibe 403 Forbidden")
    void noSoporteRecibeForbidden() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userId);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        mockMvc.perform(get("/api/orders/{orderId}/delivery-events", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
