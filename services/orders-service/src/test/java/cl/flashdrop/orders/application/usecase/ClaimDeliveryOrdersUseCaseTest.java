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
 * Cubre ORD-F7 (sin tests dedicados) y el contrato real Delivery → Orders: Delivery manda
 * el {@code userId} crudo del subject del JWT (verificado contra la implementación real
 * de Delivery, commit {@code be86777}: {@code HttpInternalOrdersClientAdapter} manda
 * {@code {"userId": <long>, "orderIds": [...]}}), NO un {@code delivery.id} ya resuelto.
 *
 * <p>{@link ClaimDeliveryOrdersUseCase#execute} es el único flujo, compartido por el
 * endpoint público legacy ({@code POST /api/delivery/claim}) y el interno delegado por
 * Delivery ({@code POST /api/internal/orders/claim}): ambos entregan un {@code userId}
 * que debe resolverse vía {@link DeliveryPort#findDeliveryIdByUserId} antes de tocar
 * {@code orderRepository}. El claim en sí SIEMPRE usa el {@code delivery.id} resuelto,
 * nunca el {@code userId} de entrada.</p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimDeliveryOrdersUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    private ClaimDeliveryOrdersUseCase useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
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

    /** Todos los tests que llegan a resolver el repartidor necesitan este stub. */
    private void resolveDeliveryFor(UUID userId) {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(DELIVERY_ID));
    }

    // ---------------------------------------------------------------
    // execute — userId → delivery.id → claim (flujo único, legacy + interno)
    // ---------------------------------------------------------------

    @Test
    void claimExitosoConMultiplesOrderIds() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        List<UUID> orderIds = List.of(orderId1, orderId2);

        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(orderIds))
                .thenReturn(List.of(claimableOrder(orderId1), claimableOrder(orderId2)));
        when(orderRepository.claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(2);

        useCase.execute(USER_ID, orderIds);

        verify(orderRepository).claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO);
        verify(deliveryPort).updateRouteStatus(orderIds, OrderStatus.EN_CAMINO.getValue());
    }

    @Test
    void resuelveUserIdADeliveryId_yElClaimUsaElDeliveryIdResuelto_noElUserId() {
        UUID orderId = UUID.randomUUID();
        List<UUID> orderIds = List.of(orderId);

        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(orderIds)).thenReturn(List.of(claimableOrder(orderId)));
        when(orderRepository.claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(1);

        useCase.execute(USER_ID, orderIds);

        // Contrato real Delivery → Orders: el userId SIEMPRE se resuelve primero...
        verify(deliveryPort, times(1)).findDeliveryIdByUserId(USER_ID);
        // ...y el claim usa el delivery.id resuelto (DELIVERY_ID) — nunca el userId de
        // entrada. DELIVERY_ID y USER_ID son UUIDs distintos generados por separado, así
        // que verificar el argumento exacto de estas dos llamadas basta para probarlo.
        verify(orderRepository).countActiveOrdersByDelivery(DELIVERY_ID);
        verify(orderRepository).claimOrders(orderIds, DELIVERY_ID, OrderStatus.EN_CAMINO);
    }

    @Test
    void usuarioSinPerfilDeRepartidor_lanzaExcepcionYNoLlegaAOrderRepository() {
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(UUID.randomUUID())));

        assertEquals("El usuario no tiene perfil de repartidor", ex.getMessage());
        verify(orderRepository, never()).claimOrders(anyList(), any(), any());
        verify(orderRepository, never()).countActiveOrdersByDelivery(any());
    }

    @Test
    void repartidorConPedidosActivos_lanzaExcepcion() {
        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(1);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(UUID.randomUUID())));

        assertEquals("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos", ex.getMessage());
        verify(orderRepository, never()).claimOrders(anyList(), any(), any());
    }

    @Test
    void excedeMaximoDePedidos_lanzaExcepcion() {
        // execute() resuelve el repartidor primero (siempre necesario para el flujo real
        // Delivery → Orders) y recién después valida la cantidad de pedidos.
        List<UUID> orderIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        resolveDeliveryFor(USER_ID);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, orderIds));

        assertEquals("Debes seleccionar entre 1 y 3 pedidos para tomar la ruta", ex.getMessage());
        verify(orderRepository, never()).countActiveOrdersByDelivery(any());
    }

    @Test
    void listaVacia_lanzaExcepcion() {
        assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of()));
    }

    @Test
    void pedidoInexistente_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(orderId)));

        assertEquals("Uno o mas pedidos ya no estan disponibles", ex.getMessage());
    }

    @Test
    void pedidoYaTomado_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        Order takenOrder = Order.builder()
                .id(orderId)
                .restaurantId(RESTAURANT_ID)
                .status(OrderStatus.EN_CAMINO)
                .build();

        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of(takenOrder));

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(orderId)));

        assertEquals("Uno o mas pedidos ya fueron tomados por otro repartidor", ex.getMessage());
    }

    @Test
    void pedidosDeDistintosRestaurantes_lanzaExcepcion() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        Order order1 = claimableOrder(orderId1);
        Order order2 = Order.builder()
                .id(orderId2)
                .restaurantId(UUID.randomUUID())
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .build();

        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId1, orderId2)))
                .thenReturn(List.of(order1, order2));

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(orderId1, orderId2)));

        assertEquals("Solo puedes agrupar pedidos del mismo restaurante", ex.getMessage());
    }

    @Test
    void conflictoDeConcurrencia_lanzaExcepcion() {
        UUID orderId = UUID.randomUUID();
        resolveDeliveryFor(USER_ID);
        when(orderRepository.countActiveOrdersByDelivery(DELIVERY_ID)).thenReturn(0);
        when(orderRepository.findByIdsForClaim(List.of(orderId))).thenReturn(List.of(claimableOrder(orderId)));
        when(orderRepository.claimOrders(List.of(orderId), DELIVERY_ID, OrderStatus.EN_CAMINO)).thenReturn(0);

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(USER_ID, List.of(orderId)));

        assertEquals("Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista", ex.getMessage());
        verify(deliveryPort, never()).updateRouteStatus(anyList(), any());
    }
}
