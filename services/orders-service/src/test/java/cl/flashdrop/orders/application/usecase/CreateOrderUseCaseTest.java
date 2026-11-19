package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.application.service.DeliveryFeeCalculator;
import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.model.DeliveryQuote;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.ReceptionMode;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;
    @Mock
    private ClientPort clientPort;
    @Mock
    private DeliveryCodePort deliveryCodePort;
    @Mock
    private DeliveryFeeCalculator deliveryFeeCalculator;
    @Mock
    private PaymentRepositoryPort paymentRepository;
    @Mock
    private PaymentGatewayPort paymentGateway;
    @Mock
    private OrderPolicyResolver policyResolver;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(orderRepository, catalogPort, clientPort, deliveryCodePort,
                deliveryFeeCalculator, paymentRepository, paymentGateway, policyResolver,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultDistanceKm", BigDecimal.valueOf(3.2));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultEstimatedMinutes", 20);
        lenient().when(deliveryCodePort.isSecureModeAvailable()).thenReturn(true);
        lenient().when(deliveryFeeCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(DeliveryQuote.fallback(BigDecimal.valueOf(2500), null));
        lenient().when(policyResolver.effectiveFor(any())).thenReturn(new OrderPolicy.Effective(15, 10, 5));
        lenient().when(paymentGateway.providerName()).thenReturn("FAKE");
        lenient().when(paymentGateway.createSession(any(), any(), any()))
                .thenReturn(new PaymentGatewayPort.PaymentSession("tx-1", "fake://flashdrop-pay/tx-1"));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia 1200")
                .paymentMethod("Tarjeta")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(2).build()
                ))
                .build();

        ProductInfo product = ProductInfo.builder()
                .id(productId)
                .restaurantId(restaurantId)
                .price(BigDecimal.valueOf(1000))
                .name("Burger")
                .available(true)
                .build();

        RestaurantInfo restaurant = RestaurantInfo.builder()
                .restaurantId(restaurantId)
                .name("Burgers House")
                .address("Los Leones 300")
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(product));
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.of(restaurant));

        Order mockSavedOrder = Order.builder()
                .id(orderId)
                .total(BigDecimal.valueOf(4500))
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(mockSavedOrder);

        CreatedOrderResult result = createOrderUseCase.execute(command);

        assertNotNull(result);
        assertEquals(orderId, result.id());
        assertEquals(0, BigDecimal.valueOf(4500).compareTo(result.total()));

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldThrowExceptionWhenProductDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()
                ))
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of());

        assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(command));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenProductIsUnavailable() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()
                ))
                .build();

        ProductInfo productNoDisponible = ProductInfo.builder()
                .id(productId)
                .restaurantId(restaurantId)
                .price(BigDecimal.valueOf(1000))
                .name("Burger")
                .available(false)
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(productNoDisponible));

        assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(command));

        verify(orderRepository, never()).save(any());
    }

    /**
     * Requisito MIGRATION_PLAN.md §12.2 "CreateOrderTest — Lista de productos vacía lanza
     * excepción": hasta ahora sólo estaba probado a nivel de dominio puro
     * (OrderDomainTest.shouldThrowExceptionWhenProductListIsEmpty), sin verificar que el
     * caso de uso completo (con sus dependencias mockeadas) realmente dispare la regla en
     * este punto exacto del flujo.
     */
    @Test
    void shouldThrowExceptionWhenItemsListIsEmpty() {
        UUID userId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia 1200")
                .items(List.of())
                .build();

        when(catalogPort.findProductsByIds(List.of())).thenReturn(List.of());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(command));

        assertEquals("El pedido debe contener al menos un producto", ex.getMessage());
        verify(orderRepository, never()).save(any());
        verify(clientPort, never()).findClientIdByUserId(any());
    }

    // ----------------- Perfil de cliente en la primera compra (acuerdo con Auth) -----------------

    private CreateOrderCommand commandFor(UUID userId, UUID productId, boolean clientProfileAllowed) {
        return CreateOrderCommand.builder()
                .userId(userId)
                .clientProfileAllowed(clientProfileAllowed)
                .address("Av. Providencia 1200")
                .paymentMethod("Tarjeta")
                .items(List.of(CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()))
                .build();
    }

    private void stubCatalogForOneProduct(UUID productId, UUID restaurantId) {
        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(ProductInfo.builder()
                .id(productId).restaurantId(restaurantId).price(BigDecimal.valueOf(1000))
                .name("Burger").available(true).build()));
    }

    @Test
    void firstPurchase_withClientRoleAndNoProfile_createsTheProfileAndUsesItInTheOrder() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID newClientId = UUID.randomUUID();
        stubCatalogForOneProduct(productId, restaurantId);
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.empty());
        when(clientPort.findOrCreateClientIdByUserId(userId)).thenReturn(newClientId);
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class)))
                .thenReturn(Order.builder().id(UUID.randomUUID()).total(BigDecimal.valueOf(3500)).build());

        createOrderUseCase.execute(commandFor(userId, productId, true));

        org.mockito.ArgumentCaptor<Order> saved = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertEquals(newClientId, saved.getValue().getClientId());
    }

    @Test
    void firstPurchase_withoutClientRole_doesNotCreateTheProfile_andFails() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        stubCatalogForOneProduct(productId, UUID.randomUUID());
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(commandFor(userId, productId, false)));

        assertEquals("No existe cliente para crear pedido", ex.getMessage());
        verify(clientPort, never()).findOrCreateClientIdByUserId(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void existingProfile_isReused_andNothingIsCreated() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        stubCatalogForOneProduct(productId, restaurantId);
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class)))
                .thenReturn(Order.builder().id(UUID.randomUUID()).total(BigDecimal.valueOf(3500)).build());

        createOrderUseCase.execute(commandFor(userId, productId, true));

        verify(clientPort, never()).findOrCreateClientIdByUserId(any());
    }

    // ------------------- Local cerrado -------------------

    @Test
    void closedStore_rejectsTheOrder_beforeSavingAnything() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        stubCatalogForOneProduct(productId, restaurantId);
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.of(
                RestaurantInfo.builder().restaurantId(restaurantId).name("Burgers House")
                        .address("Los Leones 300").open(false).build()));

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(commandFor(userId, productId, true)));

        assertEquals("El local está cerrado en este momento", ex.getMessage());
        verify(orderRepository, never()).save(any());
        verify(clientPort, never()).findOrCreateClientIdByUserId(any());
    }

    // ------------------- Modo de recepcion / codigo de entrega -------------------

    private void stubCatalogAndClient(UUID userId, UUID productId, UUID restaurantId, UUID clientId) {
        ProductInfo product = ProductInfo.builder().id(productId).restaurantId(restaurantId)
                .price(BigDecimal.valueOf(1000)).name("Burger").available(true).build();
        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(product));
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.empty());
    }

    private CreateOrderCommand commandWithMode(UUID userId, UUID productId, ReceptionMode mode) {
        return CreateOrderCommand.builder().userId(userId).address("Av. Providencia 1200")
                .paymentMethod("Tarjeta").receptionMode(mode)
                .items(List.of(CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()))
                .build();
    }

    @Test
    void modoSeguro_persisteElModoYDevuelveElCodigoAlCliente() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID(), clientId = UUID.randomUUID(), orderId = UUID.randomUUID();
        stubCatalogAndClient(userId, productId, restaurantId, clientId);
        Order saved = Order.builder().id(orderId).receptionMode(ReceptionMode.SEGURO).build();
        when(orderRepository.save(any(Order.class))).thenReturn(saved);
        when(deliveryCodePort.isSecureModeAvailable()).thenReturn(true);
        when(deliveryCodePort.currentCodeFor(saved)).thenReturn("482913");

        CreatedOrderResult result = createOrderUseCase.execute(commandWithMode(userId, productId, ReceptionMode.SEGURO));

        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(ReceptionMode.SEGURO, captor.getValue().getReceptionMode());
        assertEquals("SEGURO", result.receptionMode());
        assertEquals("482913", result.deliveryCode());
    }

    /** Sin clave el modo SEGURO se rechaza (400) antes de guardar nada: sin ruta huerfana ni 5xx. */
    @Test
    void modoSeguro_sinClave_rechazaAntesDeGuardarNada() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        when(deliveryCodePort.isSecureModeAvailable()).thenReturn(false);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(commandWithMode(userId, productId, ReceptionMode.SEGURO)));

        assertTrue(ex.getMessage().contains("SEGURO"), ex.getMessage());
        verifyNoInteractions(orderRepository, paymentRepository, paymentGateway);
    }

    @Test
    void modoNormal_esRechazadoAntesDeGuardar() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(commandWithMode(userId, productId, ReceptionMode.NORMAL)));

        assertTrue(ex.getMessage().contains("PIN"), ex.getMessage());
        verifyNoInteractions(orderRepository, paymentRepository, paymentGateway, deliveryCodePort);
    }

    // ------------------- Pago con tarjeta (F8) -------------------

    private CreateOrderCommand commandWithPayment(UUID userId, UUID productId, String paymentMethod, String key) {
        return CreateOrderCommand.builder().userId(userId).address("Av. Providencia 1200")
                .paymentMethod(paymentMethod).idempotencyKey(key)
                .items(List.of(CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()))
                .build();
    }

    @Test
    void elPedidoNaceEnPendienteDePagoConElCobroAbiertoPorElTotalDelServidor() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID(), clientId = UUID.randomUUID(), orderId = UUID.randomUUID();
        stubCatalogAndClient(userId, productId, restaurantId, clientId);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            return Order.builder().id(orderId).status(o.getStatus()).total(o.getTotal())
                    .deliveryFee(o.getDeliveryFee()).paymentExpiresAt(o.getPaymentExpiresAt())
                    .receptionMode(o.getReceptionMode()).build();
        });
        when(deliveryCodePort.currentCodeFor(any())).thenReturn("123456");

        CreatedOrderResult result = createOrderUseCase.execute(commandWithPayment(userId, productId, "Tarjeta", null));

        org.mockito.ArgumentCaptor<Order> order = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertEquals(OrderStatus.PENDIENTE_PAGO, order.getValue().getStatus());
        assertEquals(PaymentMethod.TARJETA, order.getValue().getPaymentMethod());
        assertEquals(NOW.plusMinutes(15), order.getValue().getPaymentExpiresAt());
        assertNull(order.getValue().getStoreResponseDueAt());

        org.mockito.ArgumentCaptor<Payment> payment = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(PaymentStatus.PENDIENTE, payment.getValue().getStatus());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(payment.getValue().getAmount()));
        assertEquals("tx-1", payment.getValue().getProviderTxId());

        assertEquals("Pendiente de pago", result.status());
        assertEquals("fake://flashdrop-pay/tx-1", result.paymentUrl());
        assertEquals(0, BigDecimal.valueOf(2500).compareTo(result.deliveryFee()));
        assertEquals(NOW.plusMinutes(15), result.paymentExpiresAt());
    }

    @Test
    void sinMetodoDePagoSeAsumeTarjeta() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        stubCatalogAndClient(userId, productId, UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.save(any(Order.class))).thenReturn(Order.builder().id(UUID.randomUUID())
                .total(BigDecimal.valueOf(3500)).receptionMode(ReceptionMode.SEGURO).build());
        when(deliveryCodePort.currentCodeFor(any())).thenReturn("123456");

        createOrderUseCase.execute(commandWithPayment(userId, productId, null, null));

        org.mockito.ArgumentCaptor<Order> order = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertEquals(PaymentMethod.TARJETA, order.getValue().getPaymentMethod());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Efectivo", "Transferencia", "bitcoin"})
    void efectivoTransferenciaYDesconocidosSeRechazanAntesDeGuardarNada(String method) {
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(
                commandWithPayment(UUID.randomUUID(), UUID.randomUUID(), method, null)));

        assertTrue(ex.getMessage().contains("solo se acepta Tarjeta"), ex.getMessage());
        verifyNoInteractions(orderRepository, paymentRepository, paymentGateway, catalogPort);
    }

    @Test
    void laMismaLlaveDeIdempotenciaDevuelveElMismoPedidoSinCrearOtro() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID(), clientId = UUID.randomUUID(), orderId = UUID.randomUUID();
        stubCatalogAndClient(userId, productId, restaurantId, clientId);
        Order existing = Order.builder().id(orderId).status(OrderStatus.PENDIENTE_PAGO)
                .total(BigDecimal.valueOf(3500)).deliveryFee(BigDecimal.valueOf(2500))
                .receptionMode(ReceptionMode.SEGURO).paymentExpiresAt(NOW.plusMinutes(10)).build();
        when(orderRepository.findByClientIdAndIdempotencyKey(clientId, "k-1")).thenReturn(Optional.of(existing));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(Payment.open(
                orderId, "FAKE", "tx-9", BigDecimal.valueOf(3500), NOW)));
        when(paymentGateway.paymentUrlFor("tx-9")).thenReturn("fake://flashdrop-pay/tx-9");
        when(deliveryCodePort.currentCodeFor(existing)).thenReturn("123456");

        CreatedOrderResult result = createOrderUseCase.execute(commandWithPayment(userId, productId, "Tarjeta", "k-1"));

        assertEquals(orderId, result.id());
        assertEquals("fake://flashdrop-pay/tx-9", result.paymentUrl());
        verify(orderRepository, never()).save(any());
        verify(paymentGateway, never()).createSession(any(), any(), any());
    }

    @Test
    void laLlaveDeIdempotenciaMuyLargaSeRechaza() {
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(
                commandWithPayment(UUID.randomUUID(), UUID.randomUUID(), "Tarjeta", "x".repeat(65))));

        assertTrue(ex.getMessage().contains("idempotencia"), ex.getMessage());
        verifyNoInteractions(orderRepository, paymentGateway);
    }

    @Test
    void sinCoordenadasDeDestinoElCalculadorRecibeNulosYSeCobraElRespaldo() {
        UUID userId = UUID.randomUUID(), productId = UUID.randomUUID();
        stubCatalogAndClient(userId, productId, UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.save(any(Order.class))).thenReturn(Order.builder().id(UUID.randomUUID())
                .total(BigDecimal.valueOf(3500)).receptionMode(ReceptionMode.SEGURO).build());
        when(deliveryCodePort.currentCodeFor(any())).thenReturn("123456");

        createOrderUseCase.execute(commandWithPayment(userId, productId, "Tarjeta", null));

        verify(deliveryFeeCalculator).calculate(any(), any(), eq(null), eq(null));
        org.mockito.ArgumentCaptor<Order> order = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertEquals(0, BigDecimal.valueOf(3.2).compareTo(order.getValue().getDistanceKm()));
    }

    @Test
    void sinModoInformado_seAsumeSeguro() {
        CreateOrderCommand command = CreateOrderCommand.builder().userId(UUID.randomUUID()).build();

        assertEquals(ReceptionMode.SEGURO, command.getReceptionMode());
    }
}
