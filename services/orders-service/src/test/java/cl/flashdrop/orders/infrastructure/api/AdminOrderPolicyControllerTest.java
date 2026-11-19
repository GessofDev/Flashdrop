package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.port.OrderPolicyRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderPolicyControllerTest {

    private static final String BODY = """
            {"paymentExpirationMinutes":15,
             "customerCancelWindowMinutes":5,"courierWaitMinutes":30}""";

    @Mock private OrderPolicyRepositoryPort policies;
    @Mock private OrderPolicyResolver resolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminOrderPolicyController(policies, resolver, new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticadoComo(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "9", null, Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    @Test
    void quienNoEsAdministradorNoPuedeVerNiCambiarLosPlazos_403() throws Exception {
        autenticadoComo("Restaurante");

        mockMvc.perform(get("/api/admin/order-policy")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/order-policy").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(policies, never()).save(any(), any());
    }

    @Test
    void administradorGuardaLosPlazosGlobales() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        when(resolver.effectiveFor(null)).thenReturn(new OrderPolicy.Effective(15, 8, 5, 30));

        mockMvc.perform(put("/api/admin/order-policy").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courierWaitMinutes").value(30));

        ArgumentCaptor<OrderPolicy> saved = ArgumentCaptor.forClass(OrderPolicy.class);
        verify(policies).save(saved.capture(), any());
        // Es la politica global (sin tienda) con los cuatro plazos.
        assertEquals(null, saved.getValue().getRestaurantId());
        assertEquals(8, saved.getValue().getStoreResponseMinutes());
        assertEquals(30, saved.getValue().getCourierWaitMinutes());
    }

    @Test
    void administradorVeLosPlazosVigentes() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        when(resolver.effectiveFor(null)).thenReturn(new OrderPolicy.Effective(15, 10, 5, 20));

        mockMvc.perform(get("/api/admin/order-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courierWaitMinutes").value(20));
    }

    @Test
    void plazosFueraDeRangoOIncompletosSeRechazan_400() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");

        mockMvc.perform(put("/api/admin/order-policy").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"courierWaitMinutes\":30", "\"courierWaitMinutes\":0")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/order-policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierWaitMinutes\":8}"))
                .andExpect(status().isBadRequest());
        verify(policies, never()).save(any(), any());
    }
}
