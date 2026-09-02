package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre ORD-F7 (sin tests dedicados) y verifica la semántica D5 del contrato interno
 * de claim: {@link ClaimDeliveryOrdersUseCase#executeForResolvedDelivery} NO debe
 * resolver el deliveryId vía {@link DeliveryPort#findDeliveryIdByUserId} — el valor
 * recibido ya es el definitivo (Plan_ Servicio_delivery.txt, decisión D5, cerrada).
 */
@ExtendWith(MockitoExtension.class)
class ClaimDeliveryOrdersUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    private ClaimDeliveryOrdersUseCase useCase;

    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ClaimDeliveryOrdersUseCase(orderRepository, deliveryPort);
        ReflectionTestUtils.setField(useCase, "maxClaimPerRoute", 3);
    }

    private Order claimableOrder(UUID id) {
        return Order.builder()
                .id(id)
                .restaurantId(RESTAURANT_ID)
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .build();
    }

    // ---------------------------------------------------------------
    // executeForResolvedDelivery — flujo nuevo (D5/D6)
    // ---------------------------------------------------------------

    @Test
    void executeForResolvedDelivery_claimExitosoConMultiplesOrderIds() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        List<UUID> orderIds = List.of(orderId1, orderId2);

        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(orderIds))
                .thenReturn(List.of(claimableOrder(orderId1), claimableOrder(orderId2)));
        when(orderRepository.claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(2);

        useCase.executeForResolvedDelivery(DELIVERY_ID, orderIds);

        verify(orderRepository).claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO);
        verify(deliveryPort).updateRouteStatus(orderIds, OrderStatus.EN_CAMINO.getValue());
    }

    @Test
    void executeForResolvedDelivery_usaElDeliveryIdRecibidoDirectamente_sinResolverViaUserId() {
        UUID orderId = UUID.randomUUID();
        List<UUID> orderIds = List.of(orderId);

        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(orderIds)).thenReturn(List.of(claimableOrder(orderId)));
        when(orderRepository.claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(1);

        useCase.executeForResolvedDelivery(DELIVERY_ID, orderIds);

        // D5, cerrada: el flujo nuevo NO debe resolver el deliveryId vía userId.
        verify(deliveryPort, never()).findDeliveryIdByUserId(any());
        verify(orderRepository).countActiveOrdersByDelivery(DELIVERY_ID);
        verify(orderRepository).claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO);
    }

    @Test
    void executeForResolvedDelivery_deliveryIdNulo_lanzaExcepcion() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(null, List.of(UUID.randomUUID())));

        assertEquals("El deliveryPersonId es obligatorio", ex.getMessage());
        verify(deliveryPort, never()).findDeliveryIdByUserId(any());
        verify(orderRepository, never()).claimOrders(anyList(), any(), any());
    }

    @Test
    void executeForResolvedDelivery_repartidorConPedidosActivos_lanzaExcepcion() {
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(1);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of(UUID.randomUUID())));

        assertEquals("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos", ex.getMessage());
        verify(orderRepository, never()).claimOrders(anyList(), any(), any());
    }

    @Test
    void executeForResolvedDelivery_excedeMaximoDePedidos_lanzaExcepcion() {
        List<UUID> orderIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, orderIds));

        assertEquals("Debes seleccionar entre 1 y 3 pedidos para tomar la ruta", ex.getMessage());
        verify(orderRepository, never()).countActiveOrdersByDelivery(any());
    }

    @Test
    void executeForResolvedDelivery_listaVacia_lanzaExcepcion() {
        assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of()));
    }

    @Test
    void executeForResolvedDelivery_pedidoInexistente_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of(orderId)));

        assertEquals("Uno o mas pedidos ya no estan disponibles", ex.getMessage());
    }

    @Test
    void executeForResolvedDelivery_pedidoYaTomado_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        Order takenOrder = Order.builder()
                .id(orderId)
                .restaurantId(RESTAURANT_ID)
                .status(OrderStatus.EN_CAMINO)
                .build();

        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of(takenOrder));

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of(orderId)));

        assertEquals("Uno o mas pedidos ya fueron tomados por otro repartidor", ex.getMessage());
    }

    @Test
    void executeForResolvedDelivery_pedidosDeDistintosRestaurantes_lanzaExcepcion() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        Order order1 = claimableOrder(orderId1);
        Order order2 = Order.builder()
                .id(orderId2)
                .restaurantId(UUID.randomUUID())
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .build();

        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId1, orderId2)))
                .thenReturn(List.of(order1, order2));

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of(orderId1, orderId2)));

        assertEquals("Solo puedes agrupar pedidos del mismo restaurante", ex.getMessage());
    }

    @Test
    void executeForResolvedDelivery_conflictoDeConcurrencia_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of(claimableOrder(orderId)));
        when(orderRepository.claimOrders(List.of(orderId), DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(0);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.executeForResolvedDelivery(DELIVERY_ID, List.of(orderId)));

        assertEquals("Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista", ex.getMessage());
        verify(deliveryPort, never()).updateRouteStatus(anyList(), any());
    }

    // ---------------------------------------------------------------
    // execute — flujo público legacy (/api/delivery/claim), sin cambios de comportamiento
    // ---------------------------------------------------------------

    @Test
    void execute_flujoLegacy_siResuelveDeliveryIdViaUserId() {
        UUID orderId = UUID.randomUUID();
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of(claimableOrder(orderId)));
        when(orderRepository.claimOrders(List.of(orderId), DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(1);

        useCase.execute(USER_ID, List.of(orderId));

        verify(deliveryPort, times(1)).findDeliveryIdByUserId(USER_ID);
        verify(orderRepository).claimOrders(List.of(orderId), DELIVERY_ID, OrderStatus.EN_CAMINO);
    }

    @Test
    void execute_flujoLegacy_usuarioSinPerfilDeRepartidor_lanzaExcepcion() {
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(UUID.randomUUID())));

        assertEquals("El usuario no tiene perfil de repartidor", ex.getMessage());
        verify(orderRepository, never()).claimOrders(anyList(), any(), any());
    }
}
