package cl.flashdrop.orders.config;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.CurrentUserResolver;
import cl.flashdrop.orders.infrastructure.api.DeliveryController;
import cl.flashdrop.orders.infrastructure.api.OrderController;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la cadena REAL de {@link SecurityConfig} (no un filtro aislado) contra
 * {@link OrderController} y {@link DeliveryController} — sección "Security" de la
 * auditoría 2026-09-04: "no basta con probar InternalApiKeyFilter aisladamente; debemos
 * comprobar que la configuración real de seguridad no deja endpoints protegidos
 * accidentalmente como permitAll()".
 *
 * <p>Cubre además, como regresión permanente, GAP-03 ({@code POST /api/delivery/claim}
 * exigía cero autenticación) y GAP-04 ({@code /api/orders/**} no validaba que el
 * {@code userId} del request perteneciera al usuario autenticado).</p>
 *
 * <p>{@link JwtValidationFilter} valida el JWT llamando a Auth ({@code GET /auth/validate})
 * — se simula con un {@link WireMockServer} plano (no la extensión JUnit5, para poder
 * conocer el puerto ANTES de que Spring arme el contexto vía {@code @DynamicPropertySource},
 * que corre antes de cualquier {@code @BeforeAll}).</p>
 */
@WebMvcTest(controllers = {OrderController.class, DeliveryController.class})
@Import({SecurityConfig.class, JwtValidationFilter.class, InternalApiKeyFilter.class, CurrentUserResolver.class})
class SecurityIntegrationTest {

    private static final WireMockServer AUTH_SERVER =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("auth.service.url", () -> "http://localhost:" + AUTH_SERVER.port());
        registry.add("internal.api.key", () -> "test-internal-key");
    }

    @AfterAll
    static void stopAuthServer() {
        AUTH_SERVER.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateOrderUseCase createOrderUseCase;
    @MockBean
    private GetOrderDetailUseCase getOrderDetailUseCase;
    @MockBean
    private ListOrdersUseCase listOrdersUseCase;
    @MockBean
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @MockBean
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    @BeforeEach
    void resetAuthStub() {
        AUTH_SERVER.resetAll();
    }

    private void stubAuthValidateOk() {
        AUTH_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/auth/validate"))
                .willReturn(aResponse().withStatus(200)));
    }

    /** JWT mínimo (header.payload.firma) con {@code sub} = userId — la firma no se verifica
     *  localmente, Auth ya lo hizo vía /auth/validate; JwtValidationFilter sólo lee el payload. */
    private static String jwtFor(long userId) {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"" + userId + "\"}");
        return header + "." + payload + ".sig";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // /api/orders/** — ya exigía JWT; regresión de que sigue siendo así.
    // ------------------------------------------------------------------

    @Test
    void getOrders_withoutAuthorizationHeader_returns401WithPlanContract() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        verifyNoInteractions(listOrdersUseCase);
    }

    @Test
    void getOrders_withValidJwtAndNoUserId_isAllowed() throws Exception {
        stubAuthValidateOk();
        when(listOrdersUseCase.execute(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + jwtFor(1L)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // GAP-04: ownership de user_id en GET /api/orders
    // ------------------------------------------------------------------

    @Test
    void getOrders_withUserIdMatchingAuthenticatedUser_isAllowed() throws Exception {
        stubAuthValidateOk();
        long ownUserIdLong = 7L;
        // GAP-04 + wire shape: query param user_id llega como Long (lo emite auth en el
        // sub del JWT). OrderController.listOrders hace IdConverter.toUuid(user_idLong)
        // antes de comparar contra el del token y de pasar al use case. El mock espera
        // entonces el UUID del dominio, mismo patron que OrderController produce.
        when(listOrdersUseCase.execute(IdConverter.toUuid(ownUserIdLong))).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .param("user_id", String.valueOf(ownUserIdLong))
                        .header("Authorization", "Bearer " + jwtFor(7L)))
                .andExpect(status().isOk());
    }

    @Test
    void getOrders_withUserIdOfAnotherUser_isRejectedWith403_gap04Regression() throws Exception {
        stubAuthValidateOk();
        long someoneElsesUserIdLong = 999L;

        mockMvc.perform(get("/api/orders")
                        .param("user_id", String.valueOf(someoneElsesUserIdLong))
                        .header("Authorization", "Bearer " + jwtFor(7L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(listOrdersUseCase);
    }

    @Test
    void createOrder_alwaysUsesAuthenticatedUserId_ignoringBodyUserId_gap04Regression() throws Exception {
        stubAuthValidateOk();
        long productIdLong = 8L;
        long spoofedUserIdLong = 999L;
        when(createOrderUseCase.execute(any()))
                .thenReturn(new cl.flashdrop.orders.application.dto.CreatedOrderResult(
                        UUID.randomUUID(), java.math.BigDecimal.TEN));

        // GAP-04 + wire shape: userId y productId llegan como Long (no UUID), mismo
        // numero que emiten auth y catalog respectivamente. Aqui el userId del body
        // es un numero Long arbitrario para verificar que OrderController lo IGNORA
        // y resuelve desde el JWT autenticado (5L). productId es solo un Long valido
        // para no romper la deserializacion.
        String body = """
                {"userId":%d,"address":"Av. Providencia 1200","paymentMethod":"Efectivo",
                 "productId":%d,"quantity":1}
                """.formatted(spoofedUserIdLong, productIdLong);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + jwtFor(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<cl.flashdrop.orders.application.command.CreateOrderCommand> captor =
                org.mockito.ArgumentCaptor.forClass(cl.flashdrop.orders.application.command.CreateOrderCommand.class);
        verify(createOrderUseCase).execute(captor.capture());
        assertEqualsUuid(IdConverter.toUuid(5L), captor.getValue().getUserId());
    }

    private static void assertEqualsUuid(UUID expected, UUID actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    // ------------------------------------------------------------------
    // GAP-03: POST /api/delivery/claim ahora exige JWT y la identidad sale del token.
    // ------------------------------------------------------------------

    @Test
    void postDeliveryClaim_withoutAuthorizationHeader_returns401_gap03Regression() throws Exception {
        mockMvc.perform(post("/api/delivery/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryPersonId\":\"" + UUID.randomUUID()
                                + "\",\"orderId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(claimDeliveryOrdersUseCase);
    }

    @Test
    void postDeliveryClaim_withValidJwt_resolvesIdentityFromTokenIgnoringSpoofedBody() throws Exception {
        stubAuthValidateOk();
        UUID orderId = UUID.randomUUID();
        UUID spoofedDeliveryPersonId = UUID.randomUUID();

        mockMvc.perform(post("/api/delivery/claim")
                        .header("Authorization", "Bearer " + jwtFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryPersonId\":\"" + spoofedDeliveryPersonId
                                + "\",\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isOk());

        verify(claimDeliveryOrdersUseCase).execute(IdConverter.toUuid(42L), List.of(orderId));
    }
}
