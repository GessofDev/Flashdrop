package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.dto.SalesSummary;
import cl.flashdrop.orders.application.usecase.GetRestaurantSalesSummaryUseCase;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan de pruebas §3.2: {@code GET /api/orders/restaurants/{id}/sales-summary} — rango
 * ({@code day}/{@code week}/{@code month}), conversión de ID y envelope de métricas.
 *
 * <p>Prueba de capa web con MockMvc standalone y el caso de uso <b>simulado</b>
 * ({@link GetRestaurantSalesSummaryUseCase}, Mockito), como pide el plan. La validación del
 * rango, el ownership del restaurante y los cálculos se prueban en
 * {@code GetRestaurantSalesSummaryUseCaseTest}.</p>
 *
 * <p>Los tres últimos casos (403 sin rol, usuario con varios roles, id no numérico) son
 * comportamiento del controller heredado de la versión anterior de esta prueba; no están en
 * el texto del plan y se conservan para no perder cobertura.</p>
 */
@ExtendWith(MockitoExtension.class)
class SalesSummaryControllerTest {

    @Mock private GetRestaurantSalesSummaryUseCase getRestaurantSalesSummaryUseCase;

    private MockMvc mockMvc;

    private static final long USER_ID = 2L;
    private static final UUID USER_UUID = IdConverter.toUuid(USER_ID);
    private static final UUID RESTAURANT_ID = IdConverter.toUuid(7L);
    private static final UUID PRODUCT_ID = IdConverter.toUuid(101L);
    private static final OffsetDateTime TO = OffsetDateTime.of(2026, 9, 22, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SalesSummaryController(getRestaurantSalesSummaryUseCase, new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(USER_ID), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static SalesSummary resumen(String range) {
        return new SalesSummary(RESTAURANT_ID, range, TO.minusDays(7), TO, 2,
                BigDecimal.valueOf(15000), BigDecimal.valueOf(7500),
                List.of(new SalesSummary.TopProduct(PRODUCT_ID, "Burger", 3, BigDecimal.valueOf(15000))));
    }

    @Test
    void restaurante_recibeElResumenDelCasoDeUsoDentroDelEnvelopeApiResponse_200() throws Exception {
        autenticadoComo("Restaurante");
        when(getRestaurantSalesSummaryUseCase.execute(USER_UUID, RESTAURANT_ID, "week")).thenReturn(resumen("week"));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value(7))
                .andExpect(jsonPath("$.data.range").value("week"))
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.totalRevenue").value(15000))
                .andExpect(jsonPath("$.data.averageTicket").value(7500))
                .andExpect(jsonPath("$.data.topProducts[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.topProducts[0].productName").value("Burger"))
                .andExpect(jsonPath("$.data.topProducts[0].quantitySold").value(3))
                .andExpect(jsonPath("$.data.topProducts[0].revenue").value(15000));
    }

    /** Wire Long, dominio UUID: el id del path y el usuario del token se convierten en el borde. */
    @Test
    void idLong_llegaAlCasoDeUsoConvertidoAUuid() throws Exception {
        autenticadoComo("Restaurante");
        when(getRestaurantSalesSummaryUseCase.execute(USER_UUID, RESTAURANT_ID, "week")).thenReturn(resumen("week"));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7)).andExpect(status().isOk());

        verify(getRestaurantSalesSummaryUseCase).execute(USER_UUID, RESTAURANT_ID, "week");
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "week", "month"})
    void elRangoPedido_llegaAlCasoDeUsoTalCual(String range) throws Exception {
        autenticadoComo("Restaurante");
        when(getRestaurantSalesSummaryUseCase.execute(USER_UUID, RESTAURANT_ID, range)).thenReturn(resumen(range));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7).param("range", range))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value(range));

        verify(getRestaurantSalesSummaryUseCase).execute(USER_UUID, RESTAURANT_ID, range);
    }

    @Test
    void sinRango_llegaAlCasoDeUsoElValorPorDefectoWeek() throws Exception {
        autenticadoComo("Restaurante");
        when(getRestaurantSalesSummaryUseCase.execute(USER_UUID, RESTAURANT_ID, "week")).thenReturn(resumen("week"));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7)).andExpect(status().isOk());

        verify(getRestaurantSalesSummaryUseCase).execute(USER_UUID, RESTAURANT_ID, "week");
    }

    // ---- Casos heredados (no están en el texto del plan) ----

    @Test
    void sinRolRestaurante_403_yNoInvocaElCasoDeUso() throws Exception {
        autenticadoComo("Repartidor");

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(getRestaurantSalesSummaryUseCase, never()).execute(any(), any(), any());
    }

    /** Usuario multirol (admin@demo.cl): basta con que tenga el rol Restaurante. */
    @Test
    void usuarioMultirolConRolRestaurante_puedeConsultar() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        when(getRestaurantSalesSummaryUseCase.execute(USER_UUID, RESTAURANT_ID, "week")).thenReturn(resumen("week"));

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", 7)).andExpect(status().isOk());
    }

    @Test
    void idNoNumerico_400_yNoInvocaElCasoDeUso() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/orders/restaurants/{id}/sales-summary", "abc"))
                .andExpect(status().isBadRequest());

        verify(getRestaurantSalesSummaryUseCase, never()).execute(any(), any(), any());
    }
}
