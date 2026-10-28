package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.dto.DeliveryCodeVerification;
import cl.flashdrop.orders.application.dto.DeliveryCodeVerification.Outcome;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.exception.StatusTransitionForbiddenException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.ReceptionMode;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyDeliveryCodeUseCaseTest {

    private static final UUID ORDER_ID = IdConverter.toUuid(501L);
    private static final UUID CLIENT_ID = IdConverter.toUuid(10L);
    private static final UUID USER_ID = IdConverter.toUuid(42L);
    private static final UUID DELIVERY_ID = IdConverter.toUuid(7L);
    private static final Set<Role> REPARTIDOR = Set.of(Role.REPARTIDOR);
    private static final String SECRET_CODE = "482913";

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private DeliveryPort deliveryPort;
    @Mock private ClientPort clientPort;
    @Mock private DeliveryCodePort deliveryCodePort;
    @Mock private EventPublisherPort eventPublisher;

    private VerifyDeliveryCodeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyDeliveryCodeUseCase(orderRepository, deliveryPort, clientPort, deliveryCodePort, eventPublisher);
        ReflectionTestUtils.setField(useCase, "statusUpdatedRoutingKey", "order.status.updated");
    }

    private Order givenOrder(ReceptionMode mode, OrderStatus status) {
        Order order = Order.builder().id(ORDER_ID).clientId(CLIENT_ID).deliveryId(DELIVERY_ID)
                .status(status).receptionMode(mode).build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    private void givenCourier() {
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(clientPort.findClientIdByUserId(USER_ID)).thenReturn(Optional.empty());
    }

    private void givenCodeIs(String code) {
        when(deliveryCodePort.currentCodeFor(any())).thenReturn(code);
    }

    // ---------------------------- entrega correcta ----------------------------

    @Test
    void codigoCorrecto_entregaElPedidoSincronizaDeliveryYPublicaElEvento() {
        Order order = givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        givenCourier();
        givenCodeIs(SECRET_CODE);

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID);

        assertEquals(Outcome.DELIVERED, result.outcome());
        assertNotNull(order.getDeliveryCodeVerifiedAt());
        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.ENTREGADO);
        verify(orderRepository).recordDeliveryCodeAttempt(ORDER_ID, DELIVERY_ID, 1, true);
        verify(deliveryPort).updateRouteStatusByOrder(ORDER_ID, "Entregado");
        verify(eventPublisher).publish(eq("order.status.updated"), any());
    }

    @Test
    void modoNormal_entregaConElCodigoFijo000000() {
        givenOrder(ReceptionMode.NORMAL, OrderStatus.RETIRADO);
        givenCourier();
        givenCodeIs("000000");

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, "000000", REPARTIDOR, USER_ID);

        assertEquals(Outcome.DELIVERED, result.outcome());
        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.ENTREGADO);
    }

    @Test
    void modoNormal_conCodigoIncorrectoNoEntregaNiCuentaIntentos() {
        Order order = givenOrder(ReceptionMode.NORMAL, OrderStatus.RETIRADO);
        givenCourier();
        givenCodeIs("000000");

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, "123456", REPARTIDOR, USER_ID);

        assertEquals(Outcome.WRONG_CODE, result.outcome());
        assertEquals(0, order.getDeliveryCodeAttempts());
        verify(orderRepository, never()).updateStatus(any(), any());
        verify(orderRepository, never()).updateDeliveryCodeState(any());
    }

    // ---------------------------- intentos ----------------------------

    @Test
    void codigoIncorrecto_descuentaUnIntentoYLoPersiste() {
        Order order = givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        givenCourier();
        givenCodeIs(SECRET_CODE);

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, "111111", REPARTIDOR, USER_ID);

        assertEquals(Outcome.WRONG_CODE, result.outcome());
        assertEquals(2, result.remainingAttempts());
        assertEquals(1, order.getDeliveryCodeAttempts());
        verify(orderRepository).updateDeliveryCodeState(order);
        verify(orderRepository).recordDeliveryCodeAttempt(ORDER_ID, DELIVERY_ID, 1, false);
        verify(orderRepository, never()).updateStatus(any(), any());
        verify(deliveryPort, never()).updateRouteStatusByOrder(any(), any());
    }

    @Test
    void tercerFallo_anulaElCodigoYEmiteUnoNuevo() {
        Order order = givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        order.setDeliveryCodeAttempts(2);
        givenCourier();
        givenCodeIs(SECRET_CODE);

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, "111111", REPARTIDOR, USER_ID);

        assertEquals(Outcome.CODE_RENEWED, result.outcome());
        assertEquals(3, result.remainingAttempts());
        assertEquals(2, order.getDeliveryCodeVersion());
        assertEquals(0, order.getDeliveryCodeAttempts());
        // el intento fallido se registra contra el codigo que se anulo (version 1)
        verify(orderRepository).recordDeliveryCodeAttempt(ORDER_ID, DELIVERY_ID, 1, false);
    }

    @Test
    void agotadoElSegundoCodigo_elPedidoQuedaBloqueado() {
        Order order = givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        order.setDeliveryCodeVersion(2);
        order.setDeliveryCodeAttempts(2);
        givenCourier();
        givenCodeIs(SECRET_CODE);

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, "111111", REPARTIDOR, USER_ID);

        assertEquals(Outcome.LOCKED, result.outcome());
        assertTrue(order.isDeliveryCodeLocked());
    }

    @Test
    void pedidoBloqueado_rechazaInclusoElCodigoCorrecto() {
        Order order = givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        order.setDeliveryCodeVersion(2);
        order.setDeliveryCodeAttempts(3);
        givenCourier();

        DeliveryCodeVerification result = useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID);

        assertEquals(Outcome.LOCKED, result.outcome());
        verify(orderRepository, never()).updateStatus(any(), any());
        verify(deliveryCodePort, never()).currentCodeFor(any());
    }

    // ---------------------------- quien puede y cuando ----------------------------

    @Test
    void usuarioSinRolRepartidor_recibe403() {
        givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, SECRET_CODE, Set.of(Role.CLIENTE), USER_ID));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void repartidorAjenoAlPedido_recibe403YNoSeCuentaIntento() {
        givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(IdConverter.toUuid(99L)));

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "111111", REPARTIDOR, USER_ID));

        verify(orderRepository, never()).updateDeliveryCodeState(any());
        verify(orderRepository, never()).recordDeliveryCodeAttempt(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void elClienteDuenoNoPuedeEntregarseSuPropioPedido() {
        givenOrder(ReceptionMode.SEGURO, OrderStatus.RETIRADO);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(clientPort.findClientIdByUserId(USER_ID)).thenReturn(Optional.of(CLIENT_ID));

        StatusTransitionForbiddenException ex = assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID));

        assertEquals("No puedes entregarte tu propio pedido", ex.getMessage());
        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void pedidoQueNoFueRetirado_recibe409() {
        givenOrder(ReceptionMode.SEGURO, OrderStatus.LISTO_PARA_RETIRO);
        givenCourier();

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID));

        assertTrue(ex.getMessage().startsWith("Transicion de estado no permitida"), ex.getMessage());
    }

    @Test
    void codigoDeUnSoloUso_unPedidoYaEntregadoNoAceptaOtraEntrega() {
        givenOrder(ReceptionMode.SEGURO, OrderStatus.ENTREGADO);
        givenCourier();

        assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID));

        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void pedidoInexistente_lanzaNoEncontrado() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, SECRET_CODE, REPARTIDOR, USER_ID));

        assertEquals("Pedido no encontrado", ex.getMessage());
    }
}
