package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan de pruebas §3.2: {@code GET /api/orders/available-for-delivery} — conversión de
 * {@code restaurant_id} (Long a UUID vía {@code IdConverter}), límite por query param y
 * códigos 200 y 400 si falta el parámetro obligatorio.
 *
 * <p>Prueba de capa web con MockMvc standalone y el caso de uso <b>simulado</b>
 * ({@link ListAvailableOrdersUseCase}, Mockito), como pide el plan. Las validaciones que
 * decide el caso de uso (límite entre 1 y 50, restaurante obligatorio) y el filtro de
 * pedidos disponibles se prueban en {@code ListAvailableOrdersUseCaseTest} y
 * {@code JpaOrderRepositoryAdapterTest}.</p>
 *
 * <p>Los tres últimos casos (403 sin rol, usuario con varios roles, id no numérico) son
 * comportamiento del controller heredado de la versión anterior de esta prueba; no están en
 * el texto del plan y se conservan para no perder cobertura.</p>
 */
@ExtendWith(MockitoExtension.class)
class AvailableDeliveryOrdersControllerTest {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderDetailUseCase getOrderDetailUseCase;
    @Mock private ListOrdersUseCase listOrdersUseCase;
    @Mock private UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @Mock private ListAvailableOrdersUseCase listAvailableOrdersUseCase;

    private MockMvc mockMvc;

    private static final UUID RESTAURANT_ID = IdConverter.toUuid(7L);

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(createOrderUseCase, getOrderDetailUseCase,
                listOrdersUseCase, updateOrderStatusUseCase, new CurrentUserResolver(),
                listAvailableOrdersUseCase);
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
                "42", null, Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static Order pedidoListo(long id) {
        return Order.builder()
                .id(IdConverter.toUuid(id))
                .restaurantId(RESTAURANT_ID)
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .address("Av. Providencia 1200")
                .subtotal(BigDecimal.valueOf(2000))
                .deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(4500))
                .paymentMethod(PaymentMethod.TARJETA)
                .createdAt(OffsetDateTime.now())
                .items(List.of())
                .build();
    }

    @Test
    void repartidor_recibeLaListaDelCasoDeUsoEnElMismoOrden_200() throws Exception {
        autenticadoComo("Repartidor");
        when(listAvailableOrdersUseCase.execute(RESTAURANT_ID, 5))
                .thenReturn(List.of(pedidoListo(501L), pedidoListo(502L)));

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(IdConverter.toUuid(501L).toString()))
                .andExpect(jsonPath("$.data[1].id").value(IdConverter.toUuid(502L).toString()))
                .andExpect(jsonPath("$.data[0].status").value("Listo para retiro"));
    }

    /** Wire Long, dominio UUID: la conversión se hace en el borde, antes del caso de uso. */
    @Test
    void restaurantIdLong_llegaAlCasoDeUsoConvertidoAUuid_yConElLimitePedido() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery")
                        .param("restaurant_id", "7").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(listAvailableOrdersUseCase).execute(RESTAURANT_ID, 3);
    }

    @Test
    void sinLimit_llegaAlCasoDeUsoElValorPorDefecto5() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isOk());

        verify(listAvailableOrdersUseCase).execute(RESTAURANT_ID, 5);
    }

    @Test
    void faltaRestaurantId_400_yNoInvocaElCasoDeUso() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parametro obligatorio: restaurant_id"));

        verify(listAvailableOrdersUseCase, never()).execute(any(), anyInt());
    }

    // ---- Casos heredados (no están en el texto del plan) ----

    @Test
    void sinRolRepartidor_403_yNoInvocaElCasoDeUso() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(listAvailableOrdersUseCase, never()).execute(any(), anyInt());
    }

    /** Usuario multirol (admin@demo.cl): basta con que tenga el rol Repartidor. */
    @Test
    void usuarioMultirolConRolRepartidor_puedeConsultar() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void restaurantIdNoNumerico_400_yNoInvocaElCasoDeUso() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/available-for-delivery").param("restaurant_id", "abc"))
                .andExpect(status().isBadRequest());

        verify(listAvailableOrdersUseCase, never()).execute(any(), anyInt());
    }
}
