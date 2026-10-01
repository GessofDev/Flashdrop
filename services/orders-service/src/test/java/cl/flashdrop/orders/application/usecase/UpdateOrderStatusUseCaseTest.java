package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.exception.StatusTransitionForbiddenException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre ORD-F8 (sin tests dedicados previamente) y PR-orders-status-authz (spec FR-4):
 * rol → estados que puede fijar (403), ownership del restaurante para el rol Restaurante
 * (403, IDOR — riesgo #15 del plan), asignación del pedido para el rol Repartidor (403,
 * acuerdo con delivery-service) y matriz de transición del dominio (409).
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrderStatusUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private CatalogPort catalogPort;

    private UpdateOrderStatusUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();

    private static final Set<Role> RESTAURANTE = Set.of(Role.RESTAURANTE);
    private static final Set<Role> REPARTIDOR = Set.of(Role.REPARTIDOR);

    @BeforeEach
    void setUp() {
        useCase = new UpdateOrderStatusUseCase(orderRepository, deliveryPort, eventPublisher, catalogPort);
        ReflectionTestUtils.setField(useCase, "statusUpdatedRoutingKey", "order.status.updated");
    }

    private void givenOrderWithStatus(OrderStatus status) {
        givenOrder(status, DELIVERY_ID);
    }

    private void givenOrder(OrderStatus status, UUID assignedDeliveryId) {
        Order order = Order.builder().id(ORDER_ID).restaurantId(RESTAURANT_ID)
                .deliveryId(assignedDeliveryId).status(status).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private void givenUserIsDelivery(UUID deliveryId) {
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.ofNullable(deliveryId));
    }

    private void givenUserOwnsRestaurant(UUID restaurantId) {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.ofNullable(restaurantId));
    }

    private void assertNothingPersisted() {
        verify(orderRepository, never()).updateStatus(any(), any());
        verify(deliveryPort, never()).updateRouteStatusByOrder(any(), anyString());
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void transicionValida_persisteSincronizaRutaYPublicaEvento() {
        givenOrderWithStatus(OrderStatus.NUEVO_PEDIDO);
        givenUserOwnsRestaurant(RESTAURANT_ID);

        useCase.execute(ORDER_ID, "Preparando", RESTAURANTE, USER_ID);

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.PREPARANDO);
        verify(deliveryPort).updateRouteStatusByOrder(ORDER_ID, "Preparando");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq("order.status.updated"), eventCaptor.capture());
        OrderStatusUpdatedEvent event = (OrderStatusUpdatedEvent) eventCaptor.getValue();
        assertEquals(ORDER_ID, event.getOrderId());
        assertEquals("Nuevo pedido", event.getPreviousStatus());
        assertEquals("Preparando", event.getNewStatus());
    }

    @Test
    void estadoInvalido_lanzaExcepcionSinTocarRepositorio() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, "Estado inexistente", RESTAURANTE, USER_ID));

        assertEquals("Estado no valido: Estado inexistente", ex.getMessage());
        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void pedidoNoEncontrado_lanzaExcepcion() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, "Preparando", RESTAURANTE, USER_ID));

        assertEquals("Pedido no encontrado", ex.getMessage());
        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void rolQueNoPuedeFijarElEstado_lanzaForbiddenYNoPersiste() {
        givenOrderWithStatus(OrderStatus.NUEVO_PEDIDO);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Preparando", REPARTIDOR, USER_ID));

        assertNothingPersisted();
    }

    @Test
    void clienteNoPuedeCambiarEstados() {
        givenOrderWithStatus(OrderStatus.RETIRADO);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Entregado", Set.of(Role.CLIENTE), USER_ID));

        assertNothingPersisted();
    }

    @Test
    void usuarioSinRoles_lanzaForbidden() {
        givenOrderWithStatus(OrderStatus.NUEVO_PEDIDO);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Preparando", Set.of(), USER_ID));

        assertNothingPersisted();
    }

    /** IDOR (riesgo #15): un Restaurante no puede cambiar pedidos de OTRO restaurante. */
    @Test
    void restauranteQueNoEsDuenoDelPedido_lanzaForbiddenYNoPersiste() {
        givenOrderWithStatus(OrderStatus.NUEVO_PEDIDO);
        givenUserOwnsRestaurant(UUID.randomUUID());

        StatusTransitionForbiddenException ex = assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Preparando", RESTAURANTE, USER_ID));

        assertEquals("No puedes modificar pedidos de otro restaurante", ex.getMessage());
        assertNothingPersisted();
    }

    @Test
    void restauranteSinRestauranteAsociado_lanzaForbidden() {
        givenOrderWithStatus(OrderStatus.NUEVO_PEDIDO);
        givenUserOwnsRestaurant(null);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Preparando", RESTAURANTE, USER_ID));

        assertNothingPersisted();
    }

    /** El ownership de restaurante solo aplica al rol Restaurante, no al Repartidor. */
    @Test
    void repartidor_noConsultaOwnershipDeRestaurante() {
        givenOrderWithStatus(OrderStatus.LISTO_PARA_RETIRO);
        givenUserIsDelivery(DELIVERY_ID);

        useCase.execute(ORDER_ID, "Retirado", REPARTIDOR, USER_ID);

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.RETIRADO);
        verify(catalogPort, never()).findRestaurantIdByUserId(any());
    }

    /**
     * Usuario multirol (admin@demo.cl): al retirar un pedido actúa como Repartidor, así que
     * no se le exige ser dueño del restaurante aunque también tenga el rol Restaurante.
     */
    @Test
    void usuarioMultirol_actuandoComoRepartidor_noRequiereOwnership() {
        givenOrderWithStatus(OrderStatus.LISTO_PARA_RETIRO);
        givenUserIsDelivery(DELIVERY_ID);

        useCase.execute(ORDER_ID, "Retirado", Set.of(Role.RESTAURANTE, Role.REPARTIDOR), USER_ID);

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.RETIRADO);
        verify(catalogPort, never()).findRestaurantIdByUserId(any());
    }

    /** Rol autorizado para el destino, pero el estado actual no lo permite → 409 (dominio). */
    @Test
    void transicionInvalidaParaElEstadoActual_lanzaExcepcionDeDominio() {
        givenOrderWithStatus(OrderStatus.ENTREGADO);
        givenUserIsDelivery(DELIVERY_ID);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, "Retirado", REPARTIDOR, USER_ID));

        assertTrue(ex.getMessage().startsWith("Transicion de estado no permitida"), ex.getMessage());
        assertNothingPersisted();
    }

    // ---------------------------------------------------------------
    // Acuerdo con delivery-service (Sebastián): el Repartidor solo puede cambiar el estado
    // de los pedidos asignados a él (Order.deliveryId == su delivery.id).
    // ---------------------------------------------------------------

    @Test
    void repartidorAsignado_puedeCambiarEstado() {
        givenOrderWithStatus(OrderStatus.RETIRADO);
        givenUserIsDelivery(DELIVERY_ID);

        useCase.execute(ORDER_ID, "Entregado", REPARTIDOR, USER_ID);

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.ENTREGADO);
    }

    @Test
    void repartidorNoAsignadoAlPedido_lanzaForbiddenYNoPersiste() {
        givenOrderWithStatus(OrderStatus.LISTO_PARA_RETIRO);
        givenUserIsDelivery(UUID.randomUUID());

        StatusTransitionForbiddenException ex = assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Retirado", REPARTIDOR, USER_ID));

        assertEquals("No puedes modificar pedidos asignados a otro repartidor", ex.getMessage());
        assertNothingPersisted();
    }

    /** Un pedido que nadie tomó todavía no puede ser retirado: primero hay que hacer el claim. */
    @Test
    void pedidoSinRepartidorAsignado_lanzaForbidden() {
        givenOrder(OrderStatus.LISTO_PARA_RETIRO, null);
        givenUserIsDelivery(DELIVERY_ID);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Retirado", REPARTIDOR, USER_ID));

        assertNothingPersisted();
    }

    @Test
    void usuarioConRolRepartidorSinPerfilDeRepartidor_lanzaForbidden() {
        givenOrderWithStatus(OrderStatus.LISTO_PARA_RETIRO);
        givenUserIsDelivery(null);

        assertThrows(StatusTransitionForbiddenException.class,
                () -> useCase.execute(ORDER_ID, "Retirado", REPARTIDOR, USER_ID));

        assertNothingPersisted();
    }
}
