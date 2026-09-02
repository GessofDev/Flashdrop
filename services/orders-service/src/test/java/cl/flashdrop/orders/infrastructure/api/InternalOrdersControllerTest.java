package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalOrdersControllerTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalOrdersController controller = new InternalOrdersController(orderRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnFoundOrdersAsInternalOrderDtoUsingMockMvc() throws Exception {
        UUID orderId = IdConverter.toUuid(501L);
        UUID clientId = IdConverter.toUuid(10L);
        UUID restaurantId = IdConverter.toUuid(7L);
        UUID deliveryId = IdConverter.toUuid(9L);

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .restaurantId(restaurantId)
                .deliveryId(deliveryId)
                .status(OrderStatus.EN_CAMINO)
                .address("Av. Providencia 1200, Santiago")
                .total(BigDecimal.valueOf(25000))
                .build();

        when(orderRepository.findByIds(List.of(orderId))).thenReturn(List.of(order));

        mockMvc.perform(get("/api/internal/orders").param("ids", "501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].clientId").value(10))
                .andExpect(jsonPath("$[0].restaurantId").value(7))
                .andExpect(jsonPath("$[0].deliveryId").value(9))
                .andExpect(jsonPath("$[0].status").value("En camino"))
                .andExpect(jsonPath("$[0].address").value("Av. Providencia 1200, Santiago"))
                .andExpect(jsonPath("$[0].total").doesNotExist());
    }

    @Test
    void shouldReturnNullDeliveryIdWhenOrderNotClaimedYetUsingMockMvc() throws Exception {
        UUID orderId = IdConverter.toUuid(502L);

        Order order = Order.builder()
                .id(orderId)
                .clientId(IdConverter.toUuid(10L))
                .restaurantId(IdConverter.toUuid(7L))
                .status(OrderStatus.PREPARANDO)
                .address("Av. Providencia 1200, Santiago")
                .total(BigDecimal.valueOf(25000))
                .build();

        when(orderRepository.findByIds(List.of(orderId))).thenReturn(List.of(order));

        mockMvc.perform(get("/api/internal/orders").param("ids", "502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void shouldOmitNonExistentOrdersUsingMockMvc() throws Exception {
        when(orderRepository.findByIds(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/api/internal/orders").param("ids", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldParseCommaSeparatedIdsUsingMockMvc() throws Exception {
        UUID orderId = IdConverter.toUuid(502L);
        Order order = Order.builder()
                .id(orderId)
                .clientId(IdConverter.toUuid(10L))
                .restaurantId(IdConverter.toUuid(7L))
                .status(OrderStatus.EN_CAMINO)
                .total(BigDecimal.valueOf(35000))
                .build();

        when(orderRepository.findByIds(List.of(IdConverter.toUuid(501L), orderId)))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/internal/orders").param("ids", "501,502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(502));
    }

    @Test
    void shouldRequireIdsParameterUsingMockMvc() throws Exception {
        mockMvc.perform(get("/api/internal/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Se requiere el parámetro ids"));
    }
}
