package cl.flashdrop.orders;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena de seguridad REAL (filtros incluidos) de los endpoints nuevos: el webhook de la pasarela no lleva JWT
 * (se autentica con su firma) y todo lo demas exige token. Es lo que no cubren las pruebas de controlador con
 * MockMvc independiente, que se saltan los filtros.
 */
@SpringBootTest(properties = {
        "orders.payment.webhook-secret=security-test-secret",
        "internal.api.key=test-internal-key",
        "flashdrop.orders.timeouts.courier-wait-delay-ms=3600000",
        "flashdrop.orders.recovery.pending-operations-delay-ms=3600000"
})
@AutoConfigureMockMvc
class PaymentAndAdminSecurityFullContextTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("orders_security").withUsername("orders_app").withPassword("orders_app_pass");

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void elWebhookNoExigeJwtPeroSiLaFirma() throws Exception {
        String body = "{\"providerTxId\":\"fake-1\",\"status\":\"APPROVED\"}";

        // Sin JWT llega al controlador (no es 401) y la firma ausente o mala da 403.
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/webhook").header("X-Payment-Signature", "mala")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // Con la firma correcta pasa la autenticacion y el pago desconocido da 404.
        mockMvc.perform(post("/api/payments/webhook").header("X-Payment-Signature", "security-test-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void losEndpointsDePagoCotizacionYAdministradorExigenToken() throws Exception {
        mockMvc.perform(post("/api/orders/delivery-quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/1/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/fake/fake-1/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/delivery-tariff")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/delivery-tariff").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/order-policy")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/order-policy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
