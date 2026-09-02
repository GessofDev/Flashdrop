package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el contrato del endpoint interno {@code POST /api/internal/orders/claim}.
 *
 * <p>Contrato real, verificado contra la implementación efectiva de Delivery (commit
 * {@code be86777}, {@code HttpInternalOrdersClientAdapter} +
 * {@code HttpInternalOrdersClientAdapterTest$RequestBodyShape}): el body es
 * {@code {"userId": <long>, "orderIds": [<long>, ...]}} — Delivery manda el userId
 * crudo del JWT, no un {@code delivery.id} ya resuelto.</p>
 *
 * <p>La protección mediante {@code X-Internal-Api-Key} la aplica
 * {@link cl.flashdrop.orders.config.InternalApiKeyFilter} por prefijo de ruta
 * ({@code /api/internal/}) antes de llegar a este controller — ver
 * {@code InternalApiKeyFilterTest} para la cobertura de esa capa. Este test
 * ejercita el controller + DTO + use case, asumiendo el filtro ya pasó.</p>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryClaimControllerTest {

    @Mock
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeliveryClaimController controller = new DeliveryClaimController(claimDeliveryOrdersUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void claimExitoso_conMultiplesOrderIds_delegaAlUseCaseConElUserIdCrudo() throws Exception {
        String body = """
                {
                  "userId": 42,
                  "orderIds": [501, 502]
                }
                """;

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pedido reclamado"));

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(claimDeliveryOrdersUseCase)
                .execute(userIdCaptor.capture(), orderIdsCaptor.capture());

        // El controller delega al flujo canónico execute(userId, orderIds) — la resolución
        // userId → delivery.id ocurre DENTRO del use case (ver ClaimDeliveryOrdersUseCaseTest),
        // no acá. userId=42 se convierte con el mismo IdConverter que usa todo Orders.
        assertEquals(IdConverter.toUuid(42L), userIdCaptor.getValue());
        assertEquals(List.of(IdConverter.toUuid(501L), IdConverter.toUuid(502L)), orderIdsCaptor.getValue());
    }

    @Test
    void userIdFaltante_retornaBadRequest() throws Exception {
        String body = """
                {
                  "orderIds": [501]
                }
                """;

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El userId es obligatorio"));
    }

    @Test
    void orderIdsVacio_retornaBadRequest() throws Exception {
        String body = """
                {
                  "userId": 42,
                  "orderIds": []
                }
                """;

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe indicar al menos un pedido"));
    }

    @Test
    void masDeTresOrderIds_seTraduceABadRequestConContratoDelPlan() throws Exception {
        // La regla de máximo (3) vive en el use case (orders.max-claim-per-route), no en el
        // DTO — ver ClaimDeliveryOrdersUseCaseTest.excedeMaximoDePedidos_*. Acá solo se
        // verifica que el controller propaga el 400 con el shape correcto.
        String body = """
                {
                  "userId": 42,
                  "orderIds": [501, 502, 503, 504]
                }
                """;

        doThrow(new OrderDomainException("Debes seleccionar entre 1 y 3 pedidos para tomar la ruta"))
                .when(claimDeliveryOrdersUseCase).execute(any(), anyList());

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Debes seleccionar entre 1 y 3 pedidos para tomar la ruta"));
    }

    @Test
    void deliveryInexistente_seTraduceAForbiddenConContratoDelPlan() throws Exception {
        String body = """
                {
                  "userId": 42,
                  "orderIds": [501]
                }
                """;

        doThrow(new OrderDomainException("El usuario no tiene perfil de repartidor"))
                .when(claimDeliveryOrdersUseCase).execute(any(), anyList());

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("El usuario no tiene perfil de repartidor"));
    }

    @Test
    void useCaseLanzaExcepcionDeDominio_seTraduceAErrorDeNegocioConContratoDelPlan() throws Exception {
        String body = """
                {
                  "userId": 42,
                  "orderIds": [501]
                }
                """;

        doThrow(new OrderDomainException("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos"))
                .when(claimDeliveryOrdersUseCase).execute(any(), anyList());

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos"));
    }
}
