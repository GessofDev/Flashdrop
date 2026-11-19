package cl.flashdrop.orders;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.service.UnattendedOrderTimeoutJobs;
import cl.flashdrop.orders.application.usecase.ConfirmPaymentUseCase;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.QuoteOrderUseCase;
import cl.flashdrop.orders.application.usecase.RetryPaymentUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.AdminDeliveryTariffController;
import cl.flashdrop.orders.infrastructure.api.AdminOrderPolicyController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Validacion de punta a punta del flujo de esta entrega (F7 + F8): la aplicacion COMPLETA (contexto de Spring,
 * tareas programadas habilitadas, las 14 migraciones de Flyway) contra PostgreSQL real. Solo se simulan los
 * servicios vecinos (Catalog, Delivery y el broker de eventos); la base de datos, los casos de uso, la tarifa,
 * la pasarela simulada y las tareas son los reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "orders.delivery-code.secret=lifecycle-test-secret",
        "orders.payment.webhook-secret=lifecycle-webhook",
        "internal.api.key=test-internal-key",
        // La tarea programada no debe correr sola durante la prueba: los plazos se simulan a mano.
        "flashdrop.orders.timeouts.courier-wait-delay-ms=3600000",
        "flashdrop.orders.recovery.pending-operations-delay-ms=3600000"
})
class PaidOrderLifecycleFullContextTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("orders_lifecycle").withUsername("orders_app").withPassword("orders_app_pass");

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

    @MockBean private CatalogPort catalogPort;
    @MockBean private DeliveryPort deliveryPort;
    @MockBean private EventPublisherPort eventPublisher;

    @Autowired private CreateOrderUseCase createOrder;
    @Autowired private QuoteOrderUseCase quoteOrder;
    @Autowired private ConfirmPaymentUseCase confirmPayment;
    @Autowired private RetryPaymentUseCase retryPayment;
    @Autowired private UpdateOrderStatusUseCase updateStatus;
    @Autowired private UnattendedOrderTimeoutJobs timeoutJobs;
    @Autowired private OrderRepositoryPort orders;
    @Autowired private PaymentRepositoryPort payments;
    @Autowired private AdminDeliveryTariffController adminTariff;
    @Autowired private AdminOrderPolicyController adminPolicy;

    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final UUID RESTAURANT = IdConverter.toUuid(7L);
    private static final UUID RESTAURANT_OWNER = IdConverter.toUuid(77L);

    private UUID productId;
    private UUID clientUser;

    @BeforeEach
    void setUp() {
        long n = IDS.incrementAndGet();
        productId = IdConverter.toUuid(n);
        clientUser = IdConverter.toUuid(n + 5000);
        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(ProductInfo.builder()
                .id(productId).restaurantId(RESTAURANT).name("Pizza").price(new BigDecimal("5000"))
                .available(true).dimension(3).build()));
        when(catalogPort.findRestaurantById(RESTAURANT)).thenReturn(Optional.of(RestaurantInfo.builder()
                .restaurantId(RESTAURANT).name("Local").address("Calle 1")
                .latitude(new BigDecimal("-33.4500")).longitude(new BigDecimal("-70.6600"))
                .deliveryRadiusKm(new BigDecimal("10")).build()));
        when(catalogPort.findRestaurantIdByUserId(RESTAURANT_OWNER)).thenReturn(Optional.of(RESTAURANT));
        asAdmin();
        adminTariff.replace(new AdminDeliveryTariffController.TariffBody(
                List.of(new AdminDeliveryTariffController.DimensionRangeBody(0, 4,
                                cl.flashdrop.orders.domain.model.Vehicle.BICI, new BigDecimal("500")),
                        new AdminDeliveryTariffController.DimensionRangeBody(5, null,
                                cl.flashdrop.orders.domain.model.Vehicle.MOTO, new BigDecimal("900"))),
                List.of(new AdminDeliveryTariffController.DistanceRangeBody(BigDecimal.ZERO, new BigDecimal("2"),
                                new BigDecimal("1000")),
                        new AdminDeliveryTariffController.DistanceRangeBody(new BigDecimal("2"), null,
                                new BigDecimal("2500")))));
        adminPolicy.replace(new AdminOrderPolicyController.PolicyBody(15, 5, 20));
        SecurityContextHolder.clearContext();
        clearInvocations(deliveryPort, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String userId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null,
                java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static void asAdmin() {
        authenticate("1", "Cliente", "Restaurante", "Repartidor");
    }

    private CreateOrderCommand.CreateOrderCommandBuilder command(String key) {
        return CreateOrderCommand.builder().userId(clientUser).clientProfileAllowed(true)
                .address("Av. Providencia 1200").paymentMethod("Tarjeta").idempotencyKey(key)
                .deliveryLatitude(new BigDecimal("-33.4400")).deliveryLongitude(new BigDecimal("-70.6600"))
                .items(List.of(CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(2).build()));
    }

    private CreatedOrderResult createAndPay() {
        CreatedOrderResult created = createOrder.execute(command(null).build());
        String tx = payments.findByOrderId(created.id()).get(0).getProviderTxId();
        assertEquals(ConfirmPaymentUseCase.Outcome.ORDER_ACTIVATED, confirmPayment.execute("FAKE", tx, true, "APPROVED"));
        return created;
    }

    private Order reload(UUID id) {
        return orders.findById(id).orElseThrow();
    }

    private void makeReady(UUID orderId) {
        updateStatus.execute(orderId, "Listo para retiro", Set.of(Role.RESTAURANTE), RESTAURANT_OWNER);
    }

    // ------------------------------------------------------------------ pago y envio

    @Test
    void cotizacionYCreacionCobranElMismoEnvioParametrico() {
        QuoteOrderUseCase.OrderQuote quote = quoteOrder.execute(
                List.of(new QuoteOrderUseCase.Item(productId, 2)), new BigDecimal("-33.4400"), new BigDecimal("-70.6600"));
        // dimension 2 x 3 = 6 -> MOTO ($900) + ~1,1 km ($1.000)
        assertEquals(0, new BigDecimal("1900").compareTo(quote.delivery().deliveryFee()));
        assertEquals(cl.flashdrop.orders.domain.model.Vehicle.MOTO, quote.delivery().vehicle());
        assertEquals(0, new BigDecimal("11900").compareTo(quote.total()));

        CreatedOrderResult created = createOrder.execute(command(null).build());

        assertEquals("Pendiente de pago", created.status());
        assertEquals(0, quote.total().compareTo(created.total()));
        assertNotNull(created.paymentUrl());
        assertEquals(OrderStatus.PENDIENTE_PAGO, reload(created.id()).getStatus());
        // Nada llega a Delivery ni al broker hasta que el pago se aprueba.
        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void pagoRechazadoSeReintentaYAlAprobarseLaTiendaTomaElPedido() {
        CreatedOrderResult created = createOrder.execute(command(null).build());
        String firstTx = payments.findByOrderId(created.id()).get(0).getProviderTxId();

        assertEquals(ConfirmPaymentUseCase.Outcome.PAYMENT_REJECTED,
                confirmPayment.execute("FAKE", firstTx, false, "REJECTED"));
        assertEquals(OrderStatus.PENDIENTE_PAGO, reload(created.id()).getStatus());
        verifyNoInteractions(deliveryPort);

        RetryPaymentUseCase.OpenPayment retry = retryPayment.execute(created.id(), clientUser);
        assertNotNull(retry.paymentUrl());
        List<Payment> all = payments.findByOrderId(created.id());
        assertEquals(2, all.size());
        assertEquals(PaymentStatus.PENDIENTE, all.get(0).getStatus());
        assertEquals(PaymentStatus.RECHAZADO, all.get(1).getStatus());

        assertEquals(ConfirmPaymentUseCase.Outcome.ORDER_ACTIVATED,
                confirmPayment.execute("FAKE", all.get(0).getProviderTxId(), true, "APPROVED"));

        Order paid = reload(created.id());
        assertEquals(OrderStatus.PREPARANDO, paid.getStatus());
        assertNotNull(paid.getAcceptedAt());
        assertNull(paid.getCourierDueAt(), "la espera de repartidor no corre mientras se cocina");
        ArgumentCaptor<DeliveryRoute> route = ArgumentCaptor.forClass(DeliveryRoute.class);
        verify(deliveryPort).saveRoute(route.capture());
        assertEquals(created.id(), route.getValue().getOrderId());
        assertEquals(0, new BigDecimal("-33.4500").compareTo(route.getValue().getPickupLatitude()));
        assertTrue(route.getValue().getDistanceKm().doubleValue() > 1.0
                && route.getValue().getDistanceKm().doubleValue() < 1.3, "distancia calculada, no la por defecto");
        verify(eventPublisher).publish(eq("order.created"), any());

        // Un aviso repetido de la pasarela no vuelve a crear la ruta ni el evento.
        assertEquals(ConfirmPaymentUseCase.Outcome.ALREADY_CONFIRMED,
                confirmPayment.execute("FAKE", all.get(0).getProviderTxId(), true, "APPROVED"));
        verify(deliveryPort, times(1)).saveRoute(any());
    }

    @Test
    void efectivoSeRechazaYLaMismaLlaveDevuelveElMismoPedido() {
        assertThrows(OrderDomainException.class,
                () -> createOrder.execute(command(null).paymentMethod("Efectivo").build()));

        CreatedOrderResult first = createOrder.execute(command("llave-1").build());
        CreatedOrderResult again = createOrder.execute(command("llave-1").build());
        assertEquals(first.id(), again.id());
        assertEquals(1, payments.findByOrderId(first.id()).size());
    }

    /** Cobertura de despacho (PR #111): ya no se omiten las coordenadas ni el radio del local. */
    @Test
    void sinCoordenadasDeDestinoSeRechazaPorqueLaCoberturaSiempreSeValida() {
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> createOrder.execute(
                command(null).deliveryLatitude(null).deliveryLongitude(null).build()));
        assertTrue(ex.getMessage().contains("ubicacion"), ex.getMessage());
    }

    @Test
    void unDestinoFueraDelRadioDelLocalSeRechaza() {
        // ~50 km al norte del local, que despacha hasta 10 km.
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> createOrder.execute(
                command(null).deliveryLatitude(new BigDecimal("-33.0000")).deliveryLongitude(new BigDecimal("-70.6600")).build()));
        assertTrue(ex.getMessage().contains("radio de despacho"), ex.getMessage());
    }

    @Test
    void sinTarifaCargadaSeCobraElEnvioDeRespaldoConLaCoberturaYaValidada() {
        asAdmin();
        adminTariff.replace(new AdminDeliveryTariffController.TariffBody(List.of(), List.of()));
        SecurityContextHolder.clearContext();

        CreatedOrderResult created = createOrder.execute(command(null).build());

        assertEquals(0, new BigDecimal("2500").compareTo(created.deliveryFee()));
    }

    // ------------------------------------------------------------------ pedidos sin repartidor

    @Test
    void unPedidoEnPreparandoNuncaSeCancelaPorFaltaDeRepartidor() {
        CreatedOrderResult created = createAndPay();
        orders.resetCourierDeadline(created.id(), OffsetDateTime.now().minusHours(5));

        timeoutJobs.cancelCourierWaitTimeouts();

        assertEquals(OrderStatus.PREPARANDO, reload(created.id()).getStatus());
    }

    @Test
    void listoParaRetiroSinRepartidorSeCancelaPasadaLaEsperaYSeCierraLaRuta() {
        CreatedOrderResult created = createAndPay();
        makeReady(created.id());
        Order ready = reload(created.id());
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, ready.getStatus());
        assertNotNull(ready.getCourierDueAt());
        assertTrue(ready.getCourierDueAt().isAfter(OffsetDateTime.now().plusMinutes(19)), "espera de 20 min");

        timeoutJobs.cancelCourierWaitTimeouts();
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, reload(created.id()).getStatus(), "todavia en plazo");

        orders.resetCourierDeadline(created.id(), OffsetDateTime.now().minusMinutes(1));
        timeoutJobs.cancelCourierWaitTimeouts();

        Order cancelled = reload(created.id());
        assertEquals(OrderStatus.CANCELADO, cancelled.getStatus());
        assertEquals("NO_COURIER", cancelled.getCancelReason());
        assertNotNull(cancelled.getCancelledAt());
        verify(deliveryPort).closeRoute(created.id(), "Cancelado");
    }

    @Test
    void siUnRepartidorTomaElPedidoAntesDelPlazoNoSeCancela() {
        CreatedOrderResult created = createAndPay();
        makeReady(created.id());
        assertEquals(1, orders.claimOrders(List.of(created.id()), IdConverter.toUuid(9000L)));
        orders.resetCourierDeadline(created.id(), OffsetDateTime.now().minusMinutes(1));

        timeoutJobs.cancelCourierWaitTimeouts();

        assertEquals(OrderStatus.LISTO_PARA_RETIRO, reload(created.id()).getStatus());
        verify(deliveryPort, never()).closeRoute(any(), anyString());
    }

    @Test
    void siDeliveryFallaAlCancelarSeDeshaceYSeReintenta() {
        CreatedOrderResult created = createAndPay();
        makeReady(created.id());
        orders.resetCourierDeadline(created.id(), OffsetDateTime.now().minusMinutes(1));
        doThrow(new IllegalStateException("delivery caido")).when(deliveryPort).closeRoute(any(), anyString());

        timeoutJobs.cancelCourierWaitTimeouts();
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, reload(created.id()).getStatus(), "la cancelacion se deshizo");

        doNothing().when(deliveryPort).closeRoute(any(), anyString());
        timeoutJobs.cancelCourierWaitTimeouts();
        assertEquals(OrderStatus.CANCELADO, reload(created.id()).getStatus(), "el siguiente ciclo la completa");
    }

    // ------------------------------------------------------------------ rechazo del restaurante

    @Test
    void elRestaurantePuedeRechazarEnPreparandoYSeCierraLaRuta() {
        CreatedOrderResult created = createAndPay();

        updateStatus.execute(created.id(), "Rechazado", Set.of(Role.RESTAURANTE), RESTAURANT_OWNER);

        assertEquals(OrderStatus.RECHAZADO, reload(created.id()).getStatus());
        verify(deliveryPort).closeRoute(created.id(), "Rechazado");
    }

    @Test
    void elRestaurantePuedeRechazarAunConRepartidorAsignadoYSeCancelaSuRuta() {
        CreatedOrderResult created = createAndPay();
        makeReady(created.id());
        assertEquals(1, orders.claimOrders(List.of(created.id()), IdConverter.toUuid(9001L)));
        assertNotNull(reload(created.id()).getDeliveryId());

        updateStatus.execute(created.id(), "Rechazado", Set.of(Role.RESTAURANTE), RESTAURANT_OWNER);

        assertEquals(OrderStatus.RECHAZADO, reload(created.id()).getStatus());
        verify(deliveryPort).closeRoute(created.id(), "Rechazado");
    }

    @Test
    void elClienteNoPuedeCancelarUnPedidoQueYaTieneRepartidor() {
        CreatedOrderResult created = createAndPay();
        makeReady(created.id());
        orders.claimOrders(List.of(created.id()), IdConverter.toUuid(9002L));

        assertThrows(Exception.class,
                () -> updateStatus.execute(created.id(), "Cancelado", Set.of(Role.CLIENTE), clientUser));
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, reload(created.id()).getStatus());
    }

    // ------------------------------------------------------------------ administrador

    @Test
    void soloElAdministradorEditaLosPlazosYLaTarifa() {
        authenticate("2", "Cliente", "Restaurante");
        assertThrows(AccessDeniedException.class, () -> adminPolicy.get());
        assertThrows(AccessDeniedException.class, () -> adminTariff.get());

        asAdmin();
        adminPolicy.replace(new AdminOrderPolicyController.PolicyBody(15, 5, 35));
        assertEquals(35, adminPolicy.get().getData().courierWaitMinutes());
        adminPolicy.replace(new AdminOrderPolicyController.PolicyBody(15, 5, 20));
    }

    @Test
    void lasMigracionesDejanLosPlazosGlobalesIniciales() {
        asAdmin();
        AdminOrderPolicyController.PolicyBody policy = adminPolicy.get().getData();
        assertEquals(15, policy.paymentExpirationMinutes());
        assertEquals(20, policy.courierWaitMinutes());
    }
}
