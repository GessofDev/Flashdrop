package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CancelPickedUpOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetDeliveryEventsUseCase;
import cl.flashdrop.orders.application.usecase.ReleaseAssignedOrderUseCase;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.request.CancelAfterPickupRequest;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F17: los tres endpoints de reparto y soporte aceptan el id numérico público del
 * pedido (el que emite Delivery en {@code RouteResponse.orderId}, que es {@code Long})
 * además del UUID legado.
 *
 * <p>Antes de este cambio el path variable era {@code UUID}, así que Spring rechazaba
 * el id numérico con un 400 por type mismatch y el caso de uso nunca se invocaba: la
 * app del repartidor, que tiene el id numérico en pantalla, no podía liberar ni anular
 * un pedido. El criterio de resolución es el mismo que ya aplican
 * {@code GET /api/orders/{id}}, {@code PUT /api/orders/{id}/status} y
 * {@code POST /api/orders/{id}/deliver} desde el issue #62.
 */
@ExtendWith(MockitoExtension.class)
class OrderPathIdNumericFormatTest {

    /** El id numérico que la app tiene en pantalla (Long en el contrato de Delivery). */
    private static final long NUMERIC_ORDER_ID = 501L;

    /** El UUID interno que corresponde a ese id, según el mapeo vigente del servicio. */
    private static final UUID INTERNAL_ORDER_ID = IdConverter.toUuid(NUMERIC_ORDER_ID);

    private static final UUID USER_ID = IdConverter.toUuid(42L);

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReleaseAssignedOrderUseCase releaseUseCase;

    @Mock
    private CancelPickedUpOrderUseCase cancelPickedUpOrderUseCase;

    @Mock
    private GetDeliveryEventsUseCase getDeliveryEventsUseCase;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OrderReleaseController(releaseUseCase, currentUserResolver),
                        new OrderCancellationController(cancelPickedUpOrderUseCase, currentUserResolver),
                        new OrderDeliveryEventsController(getDeliveryEventsUseCase, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── release ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Release con id numérico resuelve al pedido interno y libera")
    void release_conIdNumerico_resuelveAlPedidoInterno() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        mockMvc.perform(post("/api/orders/{orderId}/release", NUMERIC_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura de neumatico\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(releaseUseCase).execute(eq(INTERNAL_ORDER_ID), eq(USER_ID), eq("Pinchadura de neumatico"), any());
    }

    @Test
    @DisplayName("Release con UUID legado sigue funcionando")
    void release_conUuidLegado_sigueFuncionando() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        mockMvc.perform(post("/api/orders/{orderId}/release", INTERNAL_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura de neumatico\"}"))
                .andExpect(status().isOk());

        verify(releaseUseCase).execute(eq(INTERNAL_ORDER_ID), eq(USER_ID), eq("Pinchadura de neumatico"), any());
    }

    @Test
    @DisplayName("Release con id inválido responde 400 sin llegar al caso de uso")
    void release_conIdInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/release", "no-es-un-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Pinchadura de neumatico\"}"))
                .andExpect(status().isBadRequest());

        verify(releaseUseCase, never()).execute(any(), any(), any(), any());
    }

    // ── cancel-after-pickup ────────────────────────────────────────────────────

    @Test
    @DisplayName("Cancel-after-pickup con id numérico resuelve al pedido interno y anula")
    void cancelAfterPickup_conIdNumerico_resuelveAlPedidoInterno() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.SOPORTE));

        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "Cliente ausente");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", NUMERIC_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cancelPickedUpOrderUseCase).execute(
                eq(INTERNAL_ORDER_ID), eq(USER_ID), eq(Role.SOPORTE),
                eq(OrderStatus.CANCELADO), eq("Cliente ausente"), any());
    }

    @Test
    @DisplayName("Cancel-after-pickup con UUID legado sigue funcionando")
    void cancelAfterPickup_conUuidLegado_sigueFuncionando() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.REPARTIDOR));

        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "Cliente ausente");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", INTERNAL_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(cancelPickedUpOrderUseCase).execute(
                eq(INTERNAL_ORDER_ID), eq(USER_ID), eq(Role.REPARTIDOR),
                eq(OrderStatus.CANCELADO), eq("Cliente ausente"), any());
    }

    @Test
    @DisplayName("Cancel-after-pickup con id inválido responde 400 sin llegar al caso de uso")
    void cancelAfterPickup_conIdInvalido_responde400() throws Exception {
        CancelAfterPickupRequest request = new CancelAfterPickupRequest("Cancelado", "Cliente ausente");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-after-pickup", "no-es-un-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(cancelPickedUpOrderUseCase, never()).execute(any(), any(), any(), any(), any(), any());
    }

    // ── delivery-events ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delivery-events con id numérico resuelve al pedido interno")
    void deliveryEvents_conIdNumerico_resuelveAlPedidoInterno() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.SOPORTE));
        when(getDeliveryEventsUseCase.execute(INTERNAL_ORDER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/{orderId}/delivery-events", NUMERIC_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(getDeliveryEventsUseCase).execute(INTERNAL_ORDER_ID);
    }

    @Test
    @DisplayName("Delivery-events con UUID legado sigue funcionando")
    void deliveryEvents_conUuidLegado_sigueFuncionando() throws Exception {
        when(currentUserResolver.requireCurrentUserId()).thenReturn(USER_ID);
        when(currentUserResolver.requireCurrentRoles()).thenReturn(Set.of(Role.SOPORTE));
        when(getDeliveryEventsUseCase.execute(INTERNAL_ORDER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/{orderId}/delivery-events", INTERNAL_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(getDeliveryEventsUseCase).execute(INTERNAL_ORDER_ID);
    }

    @Test
    @DisplayName("Delivery-events con id inválido responde 400 sin llegar al caso de uso")
    void deliveryEvents_conIdInvalido_responde400() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}/delivery-events", "no-es-un-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(getDeliveryEventsUseCase, never()).execute(any());
    }
}
