package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ConfirmPaymentUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.payment.FakePaymentGateway;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllersTest {

    @Mock private ConfirmPaymentUseCase confirmPaymentUseCase;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private OrderRepositoryPort orderRepository;
    @Mock private ClientPort clientPort;

    private final FakePaymentGateway gateway = new FakePaymentGateway("s3cret");
    private MockMvc webhook;
    private MockMvc simulator;

    private final UUID orderId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webhook = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(gateway, confirmPaymentUseCase))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        simulator = MockMvcBuilders.standaloneSetup(new FakePaymentSimulatorController(gateway, paymentRepository,
                        orderRepository, clientPort, confirmPaymentUseCase, new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticadoComo(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_Cliente"))));
    }

    // ------------------- Webhook -------------------

    @Test
    void webhookConFirmaValidaAplicaElResultado() throws Exception {
        when(confirmPaymentUseCase.execute("FAKE", "tx-1", true, "APPROVED"))
                .thenReturn(ConfirmPaymentUseCase.Outcome.ORDER_ACTIVATED);

        webhook.perform(post("/api/payments/webhook").header("X-Payment-Signature", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerTxId\":\"tx-1\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ORDER_ACTIVATED"));
    }

    @Test
    void webhookRechazadoSeTraduceAPagoRechazado() throws Exception {
        when(confirmPaymentUseCase.execute("FAKE", "tx-1", false, "REJECTED"))
                .thenReturn(ConfirmPaymentUseCase.Outcome.PAYMENT_REJECTED);

        webhook.perform(post("/api/payments/webhook").header("X-Payment-Signature", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerTxId\":\"tx-1\",\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PAYMENT_REJECTED"));
    }

    @Test
    void webhookSinFirmaOConFirmaMalaDa403YNoTocaNada() throws Exception {
        webhook.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerTxId\":\"tx-1\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        webhook.perform(post("/api/payments/webhook").header("X-Payment-Signature", "mala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerTxId\":\"tx-1\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        verify(confirmPaymentUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void webhookConEstadoDesconocidoDa400() throws Exception {
        webhook.perform(post("/api/payments/webhook").header("X-Payment-Signature", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerTxId\":\"tx-1\",\"status\":\"maybe\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------- Simulador -------------------

    private void stubPaymentOfOrder() {
        when(paymentRepository.findByProviderAndProviderTxId("FAKE", "tx-1")).thenReturn(Optional.of(
                Payment.open(orderId, "FAKE", "tx-1", BigDecimal.TEN, OffsetDateTime.now())));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).clientId(clientId).status(OrderStatus.PENDIENTE_PAGO).build()));
    }

    @Test
    void elClienteDuenoPuedeAprobarSuPagoSimulado() throws Exception {
        autenticadoComo("5");
        stubPaymentOfOrder();
        when(clientPort.findClientIdByUserId(any())).thenReturn(Optional.of(clientId));
        when(confirmPaymentUseCase.execute(eq("FAKE"), eq("tx-1"), eq(true), any()))
                .thenReturn(ConfirmPaymentUseCase.Outcome.ORDER_ACTIVATED);

        simulator.perform(post("/api/payments/fake/tx-1/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ORDER_ACTIVATED"));
    }

    @Test
    void elClienteDuenoPuedeRechazarSuPagoSimulado() throws Exception {
        autenticadoComo("5");
        stubPaymentOfOrder();
        when(clientPort.findClientIdByUserId(any())).thenReturn(Optional.of(clientId));
        when(confirmPaymentUseCase.execute(eq("FAKE"), eq("tx-1"), eq(false), any()))
                .thenReturn(ConfirmPaymentUseCase.Outcome.PAYMENT_REJECTED);

        simulator.perform(post("/api/payments/fake/tx-1/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PAYMENT_REJECTED"));
    }

    @Test
    void otroClienteNoPuedeResolverElPagoAjeno() throws Exception {
        autenticadoComo("6");
        stubPaymentOfOrder();
        when(clientPort.findClientIdByUserId(any())).thenReturn(Optional.of(UUID.randomUUID()));

        simulator.perform(post("/api/payments/fake/tx-1/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        verify(confirmPaymentUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
