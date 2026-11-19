package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.model.CancellationReason;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F7 contra PostgreSQL real con las migraciones reales: aceptacion de la tienda, busqueda de pedidos vencidos
 * y cancelacion condicional (no pisa una aceptacion ni a un repartidor que toma el pedido).
 */
class UnattendedOrdersDbTest extends PostgresIntegrationTestSupport {

    @Autowired private SpringDataOrderRepository orderRepository;
    @Autowired private SpringDataOrderItemRepository orderItemRepository;
    @Autowired private SpringDataOrderDeliveryCodeAttemptRepository attemptRepository;
    @Autowired private SpringDataClientRepository clientRepository;
    @Autowired private SpringDataOrderPolicyRepository policyRepository;

    private JpaOrderRepositoryAdapter orders;
    private UUID clientId;
    private final OffsetDateTime now = OffsetDateTime.now().withNano(0);

    @BeforeEach
    void setUp() {
        orders = new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository, attemptRepository);
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()).createdAt(OffsetDateTime.now()).build());
        clientId = IdConverter.toUuid(client.getId());
    }

    private Order order(OrderStatus status, OffsetDateTime storeDue, OffsetDateTime courierDue, UUID deliveryId) {
        return orders.save(Order.builder().clientId(clientId).restaurantId(IdConverter.toUuid(7L))
                .deliveryId(deliveryId).status(status).address("Calle 1")
                .storeResponseDueAt(storeDue).courierDueAt(courierDue)
                .subtotal(BigDecimal.valueOf(7500)).deliveryFee(BigDecimal.valueOf(2500)).total(BigDecimal.valueOf(10000))
                .paymentMethod(PaymentMethod.TARJETA).items(List.of()).build());
    }

    private List<UUID> ids(List<Order> list) {
        return list.stream().map(Order::getId).toList();
    }

    // ------------------------- tienda sin respuesta -------------------------

    @Test
    void soloSeEncuentranLosNuevosPedidosConElPlazoDeLaTiendaVencido() {
        Order vencido = order(OrderStatus.NUEVO_PEDIDO, now.minusMinutes(1), null, null);
        Order enPlazo = order(OrderStatus.NUEVO_PEDIDO, now.plusMinutes(5), null, null);
        Order sinPlazo = order(OrderStatus.NUEVO_PEDIDO, null, null, null); // pedido anterior a F7
        Order yaAceptado = order(OrderStatus.PREPARANDO, now.minusMinutes(9), null, null);

        List<UUID> found = ids(orders.findStoreResponseOverdue(now, 100));

        assertTrue(found.contains(vencido.getId()));
        assertFalse(found.contains(enPlazo.getId()));
        assertFalse(found.contains(sinPlazo.getId()));
        assertFalse(found.contains(yaAceptado.getId()));
    }

    @Test
    void cancelarPorTiendaSinRespuestaDejaMotivoYHora() {
        Order vencido = order(OrderStatus.NUEVO_PEDIDO, now.minusMinutes(1), null, null);

        assertTrue(orders.cancelUnattended(vencido.getId(), OrderStatus.NUEVO_PEDIDO,
                CancellationReason.STORE_NO_RESPONSE, now));

        Order loaded = orders.findById(vencido.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELADO, loaded.getStatus());
        assertEquals("STORE_NO_RESPONSE", loaded.getCancelReason());
        assertEquals(now.toInstant(), loaded.getCancelledAt().toInstant());
        // Ya cancelado: no vuelve a salir en la busqueda ni se cancela dos veces.
        assertFalse(ids(orders.findStoreResponseOverdue(now, 100)).contains(vencido.getId()));
        assertFalse(orders.cancelUnattended(vencido.getId(), OrderStatus.NUEVO_PEDIDO,
                CancellationReason.STORE_NO_RESPONSE, now));
    }

    @Test
    void siLaTiendaAceptaJustoAntesLaCancelacionNoLoPisa() {
        Order pedido = order(OrderStatus.NUEVO_PEDIDO, now.minusMinutes(1), null, null);

        assertTrue(orders.acceptByStore(pedido.getId(), OrderStatus.NUEVO_PEDIDO, OrderStatus.PREPARANDO,
                now, now.plusMinutes(20)));

        assertFalse(orders.cancelUnattended(pedido.getId(), OrderStatus.NUEVO_PEDIDO,
                CancellationReason.STORE_NO_RESPONSE, now));
        assertEquals(OrderStatus.PREPARANDO, orders.findById(pedido.getId()).orElseThrow().getStatus());
    }

    // ------------------------- aceptacion de la tienda -------------------------

    @Test
    void aceptarGuardaLaHoraYElPlazoParaEncontrarRepartidor() {
        Order pedido = order(OrderStatus.NUEVO_PEDIDO, now.plusMinutes(5), null, null);

        assertTrue(orders.acceptByStore(pedido.getId(), OrderStatus.NUEVO_PEDIDO, OrderStatus.LISTO_PARA_RETIRO,
                now, now.plusMinutes(20)));

        Order loaded = orders.findById(pedido.getId()).orElseThrow();
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, loaded.getStatus());
        assertEquals(now.toInstant(), loaded.getAcceptedAt().toInstant());
        assertEquals(now.plusMinutes(20).toInstant(), loaded.getCourierDueAt().toInstant());
        // Una segunda aceptacion (o una carrera) no encuentra el pedido en Nuevo pedido.
        assertFalse(orders.acceptByStore(pedido.getId(), OrderStatus.NUEVO_PEDIDO, OrderStatus.PREPARANDO,
                now, now.plusMinutes(99)));
    }

    // ------------------------- sin repartidor -------------------------

    @Test
    void soloSeEncuentranLosAceptadosSinRepartidorConLaEsperaVencida() {
        UUID repartidor = IdConverter.toUuid(9L);
        Order sinRepartidor = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), null);
        Order preparando = order(OrderStatus.PREPARANDO, null, now.minusMinutes(3), null);
        Order conRepartidor = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), repartidor);
        Order enPlazo = order(OrderStatus.LISTO_PARA_RETIRO, null, now.plusMinutes(5), null);
        Order nuevo = order(OrderStatus.NUEVO_PEDIDO, null, now.minusMinutes(1), null);
        Order retirado = order(OrderStatus.RETIRADO, null, now.minusMinutes(1), repartidor);

        List<UUID> found = ids(orders.findCourierWaitOverdue(now, 100));

        assertTrue(found.contains(sinRepartidor.getId()));
        assertTrue(found.contains(preparando.getId()));
        assertFalse(found.contains(conRepartidor.getId()));
        assertFalse(found.contains(enPlazo.getId()));
        assertFalse(found.contains(nuevo.getId()));
        assertFalse(found.contains(retirado.getId()));
    }

    @Test
    void siUnRepartidorTomaElPedidoJustoAntesLaCancelacionNoLoPisa() {
        Order pedido = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), null);
        assertEquals(1, orders.claimOrders(List.of(pedido.getId()), IdConverter.toUuid(9L)));

        assertFalse(orders.cancelUnattended(pedido.getId(), OrderStatus.LISTO_PARA_RETIRO,
                CancellationReason.NO_COURIER, now));
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, orders.findById(pedido.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelarPorFaltaDeRepartidorDejaElMotivo() {
        Order pedido = order(OrderStatus.PREPARANDO, null, now.minusMinutes(1), null);

        assertTrue(orders.cancelUnattended(pedido.getId(), OrderStatus.PREPARANDO, CancellationReason.NO_COURIER, now));

        Order loaded = orders.findById(pedido.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELADO, loaded.getStatus());
        assertEquals("NO_COURIER", loaded.getCancelReason());
    }

    @Test
    void alLiberarseElPedidoSeVuelveAEsperarUnRepartidor() {
        Order pedido = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(5), null);

        orders.resetCourierDeadline(pedido.getId(), now.plusMinutes(20));

        assertFalse(ids(orders.findCourierWaitOverdue(now, 100)).contains(pedido.getId()));
        assertEquals(now.plusMinutes(20).toInstant(),
                orders.findById(pedido.getId()).orElseThrow().getCourierDueAt().toInstant());
    }

    // ------------------------- plazos globales -------------------------

    @Test
    void laEsperaDeRepartidorSeSiembraSeGuardaYSoloEsGlobal() {
        JpaOrderPolicyRepositoryAdapter policies = new JpaOrderPolicyRepositoryAdapter(policyRepository);
        OrderPolicyResolver resolver = new OrderPolicyResolver(policies);
        // V14 siembra la espera global en 20 minutos.
        assertEquals(20, resolver.effectiveFor(null).courierWaitMinutes());

        policies.save(OrderPolicy.builder().paymentExpirationMinutes(15).storeResponseMinutes(8)
                .customerCancelWindowMinutes(5).courierWaitMinutes(35).build(), IdConverter.toUuid(5L));
        // Una tienda que intente fijar sus propios plazos de F7: se ignoran.
        UUID tienda = IdConverter.toUuid(7L);
        policies.save(OrderPolicy.builder().restaurantId(tienda).storeResponseMinutes(1).courierWaitMinutes(1).build(),
                IdConverter.toUuid(5L));
        policyRepository.flush();

        OrderPolicy.Effective e = resolver.effectiveFor(tienda);
        assertEquals(8, e.storeResponseMinutes());
        assertEquals(35, e.courierWaitMinutes());
    }
}
