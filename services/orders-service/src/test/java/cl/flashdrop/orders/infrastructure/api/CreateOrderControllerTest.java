package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el contrato del endpoint público de creación de pedido
 * ({@code POST /api/orders}), enfocándose en el wire shape que descubrió el QA Floci:
 *
 * <ol>
 *   <li>userId y productId llegan como <b>Long</b> en el JSON (no UUID), igual que
 *       los emite catalog-service (productId) y auth-service (userId en el sub del JWT).
 *       Esta es la decisión de wire shape que cerró D5/D6 para
 *       {@code /api/internal/orders/claim} — ahora también para {@code POST /api/orders}.</li>
 *   <li>El controller convierte Long → UUID en el límite vía {@link IdConverter#toUuid}
 *       antes de pasar al use case (que trabaja en UUID del dominio interno).</li>
 *   <li>Un payload malformado (JSON inválido, tipo incoherente) se traduce a 400 +
 *       {@code BAD_REQUEST}, no a 500. Antes caía al {@code handleGenericException}
 *       genérico y devolvía 500/INTERNAL_ERROR con el mensaje crudo de Jackson.</li>
 * </ol>
 *
 * <p>El controller delega la identidad del usuario al {@code CurrentUserResolver}
 * (GAP-04): el {@code userId} del body se ignora a propósito. El mock del resolver
 * fija el userId "1L" — coincide con el "8" del usuario de prueba que registró
 * Nicolás en Floci y otros valores Long según el caso.</p>
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderControllerTest {

    @Mock
    private CreateOrderUseCase createOrderUseCase;
    @Mock
    private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock
    private ListOrdersUseCase listOrdersUseCase;
    @Mock
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
                createOrderUseCase, getOrderDetailUseCase, listOrdersUseCase,
                updateOrderStatusUseCase, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Cubre la regresión exacta del bug que reportó Nicolás: cuando el wire era UUID
     * y el cliente mandaba Long (catalog/auth), Jackson lanzaba
     * {@code HttpMessageNotReadableException} y el {@code handleGenericException} lo
     * traducía a 500/INTERNAL_ERROR con "Cannot deserialize value of type java.util.UUID
     * from String '1'". Ahora, gracias al nuevo handler específico en
     * {@link GlobalExceptionHandler#handleNotReadable}, eso devuelve 400/BAD_REQUEST,
     * independientemente del tipo del campo.
     */
    @Test
    void payloadConUserIdNoNumerico_retornaBadRequest() throws Exception {
        String body = """
                {
                  "userId": "not-a-number",
                  "address": "Av. Providencia 1200",
                  "items": [{"productId": 1, "quantity": 2}]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Cuerpo del request invalido o malformado"));

        verify(createOrderUseCase, never()).execute(any());
    }

    /**
     * Cubre la regresión con JSON completamente malformado — distinto path (Jackson no
     * puede parsear la forma) pero misma familia de excepción, mismo 400/BAD_REQUEST.
     */
    @Test
    void jsonMalformado_retornaBadRequest() throws Exception {
        String body = "{ esto no es json valido ";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        verify(createOrderUseCase, never()).execute(any());
    }

    /**
     * Cubre el camino feliz del fix: wire shape con Long (catalog/auth emiten Long).
     * Verifica que el controller:
     * <ul>
     *   <li>acepta el body sin error;</li>
     *   <li>resuelve el userId vía JWT (no del body — GAP-04);</li>
     *   <li>convierte productId 101 (Long) a {@code IdConverter.toUuid(101L)} antes
     *       de pasar al use case;</li>
     *   <li>responde 201 Created con {@code ApiResponse.success("Pedido creado", result)}.</li>
     * </ul>
     */
    @Test
    void crearPedidoConLongValidos_delegaAlUseCaseConUUIDsConvertidos() throws Exception {
        String body = """
                {
                  "address": "Av. Providencia 1200",
                  "paymentMethod": "Tarjeta",
                  "items": [{"productId": 101, "quantity": 2}]
                }
                """;

        UUID userUuidFromJwt = IdConverter.toUuid(1L);
        UUID orderUuid = IdConverter.toUuid(501L);
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userUuidFromJwt);
        when(createOrderUseCase.execute(any()))
                .thenReturn(new CreatedOrderResult(orderUuid, new BigDecimal("4500")));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pedido creado"))
                .andExpect(jsonPath("$.data.id").value(orderUuid.toString()));

        ArgumentCaptor<CreateOrderCommand> captor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(createOrderUseCase).execute(captor.capture());

        CreateOrderCommand cmd = captor.getValue();
        assertEquals(userUuidFromJwt, cmd.getUserId(),
                "el userId del comando viene del JWT, no del body (GAP-04)");
        assertEquals(1, cmd.getItems().size());
        assertEquals(IdConverter.toUuid(101L), cmd.getItems().get(0).getProductId(),
                "productId 101 Long se convirtio a UUID del dominio via IdConverter");
        assertEquals(2, cmd.getItems().get(0).getQuantity());
    }

    /**
     * Variante del happy path con formato legacy: {@code productId} + {@code quantity}
     * en el top-level en lugar de {@code items[]}. Misma conversión Long → UUID.
     */
    @Test
    void crearPedidoFormatoLegacy_delegaAlUseCaseConUUIDsConvertidos() throws Exception {
        String body = """
                {
                  "address": "Av. Providencia 1200",
                  "paymentMethod": "Efectivo",
                  "productId": 7,
                  "quantity": 1
                }
                """;

        UUID userUuidFromJwt = IdConverter.toUuid(1L);
        UUID orderUuid = IdConverter.toUuid(502L);
        when(currentUserResolver.requireCurrentUserId()).thenReturn(userUuidFromJwt);
        when(createOrderUseCase.execute(any()))
                .thenReturn(new CreatedOrderResult(orderUuid, new BigDecimal("1500")));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<CreateOrderCommand> captor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(createOrderUseCase).execute(captor.capture());

        CreateOrderCommand cmd = captor.getValue();
        List<CreateOrderCommand.ItemRequest> items = cmd.getItems();
        assertEquals(1, items.size());
        assertEquals(IdConverter.toUuid(7L), items.get(0).getProductId(),
                "productId legacy 7 (Long) se convirtio a UUID");
        assertEquals(1, items.get(0).getQuantity());
    }
}
