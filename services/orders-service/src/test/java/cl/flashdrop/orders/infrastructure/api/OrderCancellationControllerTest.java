package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CancelPickedUpOrderUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.api.dto.request.CancelAfterPickupRequest;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderCancellationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CancelPickedUpOrderUseCase cancelPickedUpOrderUseCase;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private OrderCancellationController controller;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Repartidor cancela pedido retirado exitosamente")
    void cancelaComoRepartidorExitosamente() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userId);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "Pinchazo de neumático");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Correlation-Id", "corr-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pedido anulado exitosamente tras retiro"));

        verify(cancelPickedUpOrderUseCase).execute(
                eq(orderId), eq(userId), eq(Role.REPARTIDOR), eq(OrderStatus.CANCELADO), eq("Pinchazo de neumático"), eq("corr-123"));
    }

    @Test
    @DisplayName("Soporte rechaza pedido retirado exitosamente y prevalece sobre repartidor si ambos roles estan presentes")
    void cancelaComoSoportePrevalece() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userId);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR, Role.SOPORTE));

        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Rechazado", "Cliente ausente en domicilio");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cancelPickedUpOrderUseCase).execute(
                eq(orderId), eq(userId), eq(Role.SOPORTE), eq(OrderStatus.RECHAZADO), eq("Cliente ausente en domicilio"), isNull());
    }

    @Test
    @DisplayName("Rol no autorizado (Cliente o Restaurante) produce 403 Forbidden")
    void rolNoAutorizadoDevuelve403() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userId);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.CLIENTE));

        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "Motivo valido");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(cancelPickedUpOrderUseCase, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Estado final inválido (ej: Entregado o Nuevo pedido) produce 400 Bad Request")
    void estadoInvalidoDevuelve400() throws Exception {
        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Entregado", "No aplica");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(cancelPickedUpOrderUseCase, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Motivo vacío o en blanco produce 400 Bad Request")
    void motivoVacioDevuelve400() throws Exception {
        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "   ");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(cancelPickedUpOrderUseCase, never()).execute(any(), any(), any(), any(), any(), any());
    }
}
