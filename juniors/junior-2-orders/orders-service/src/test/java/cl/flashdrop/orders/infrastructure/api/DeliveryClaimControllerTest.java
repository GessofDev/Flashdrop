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
 * Cubre el contrato del endpoint interno {@code POST /api/internal/orders/claim}
 * (decisiones D5/D6 de "Plan_ Servicio_delivery.txt").
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
    void claimExitoso_conMultiplesOrderIds_delegaAlUseCaseConDeliveryIdDirecto() throws Exception {
        String body = """
                {
                  "deliveryPersonId": 42,
                  "orderIds": [501, 502]
                }
                """;

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pedido reclamado"));

        ArgumentCaptor<UUID> deliveryIdCaptor = ArgumentCaptor.forClass(UUID.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> orderIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(claimDeliveryOrdersUseCase)
                .executeForResolvedDelivery(deliveryIdCaptor.capture(), orderIdsCaptor.capture());

        // deliveryPersonId=42 se usa DIRECTO (mismo mapeo que IdConverter usa en todo Orders
        // para representar Longs externos como UUID de dominio) — sin pasar por resolución.
        assertEquals(IdConverter.toUuid(42L), deliveryIdCaptor.getValue());
        assertEquals(List.of(IdConverter.toUuid(501L), IdConverter.toUuid(502L)), orderIdsCaptor.getValue());
    }

    @Test
    void deliveryPersonIdFaltante_retornaBadRequest() throws Exception {
        String body = """
                {
                  "orderIds": [501]
                }
                """;

        mockMvc.perform(post("/api/internal/orders/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El deliveryPersonId es obligatorio"));
    }

    @Test
    void orderIdsVacio_retornaBadRequest() throws Exception {
        String body = """
                {
                  "deliveryPersonId": 42,
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
    void useCaseLanzaExcepcionDeDominio_seTraduceAErrorDeNegocioConContratoDelPlan() throws Exception {
        String body = """
                {
                  "deliveryPersonId": 42,
                  "orderIds": [501]
                }
                """;

        doThrow(new OrderDomainException("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos"))
                .when(claimDeliveryOrdersUseCase).executeForResolvedDelivery(any(), anyList());

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
