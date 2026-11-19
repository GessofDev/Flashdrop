package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.port.DeliveryTariffRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDeliveryTariffControllerTest {

    private static final String BODY = """
            {"dimensionRanges":[
               {"minDimension":0,"maxDimension":4,"vehicle":"BICI","price":500},
               {"minDimension":5,"vehicle":"AUTO","price":2000}],
             "distanceRanges":[
               {"minKm":0,"maxKm":2,"price":1000},
               {"minKm":2,"price":2500}]}""";

    @Mock private DeliveryTariffRepositoryPort tariffs;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminDeliveryTariffController(tariffs, new CurrentUserResolver()))
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
    void sinLosTresRolesNoPuedeVerNiCambiarLaTarifa_403() throws Exception {
        autenticadoComo("Cliente", "Restaurante");

        mockMvc.perform(get("/api/admin/delivery-tariff")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/delivery-tariff").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(tariffs, never()).replace(any(), any());
    }

    @Test
    void administradorGuardaLaTarifaYLaRecibeDeVuelta() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        when(tariffs.replace(any(), any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/admin/delivery-tariff").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimensionRanges[0].vehicle").value("BICI"))
                .andExpect(jsonPath("$.data.dimensionRanges[1].vehicle").value("AUTO"))
                .andExpect(jsonPath("$.data.distanceRanges[1].price").value(2500));
    }

    @Test
    void administradorVeLaTarifaVigente() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        when(tariffs.find()).thenReturn(DeliveryTariff.empty());

        mockMvc.perform(get("/api/admin/delivery-tariff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimensionRanges").isEmpty());
    }

    @Test
    void precioNegativoSeRechazaConUnErrorDeValidacion() throws Exception {
        autenticadoComo("Cliente", "Restaurante", "Repartidor");
        String malo = BODY.replace("\"price\":500", "\"price\":-1");

        mockMvc.perform(put("/api/admin/delivery-tariff").contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest());
        verify(tariffs, never()).replace(any(), any());
    }
}
