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

    // ------------------------- aceptacion manual (pedidos antiguos en Nuevo pedido) -------------------------

    @Test
    void aceptarAMano_guardaLaHoraDeToma() {
        Order pedido = order(OrderStatus.NUEVO_PEDIDO, now.plusMinutes(5), null, null);

        assertTrue(orders.acceptByStore(pedido.getId(), OrderStatus.NUEVO_PEDIDO, OrderStatus.PREPARANDO, now, null));

        Order loaded = orders.findById(pedido.getId()).orElseThrow();
        assertEquals(OrderStatus.PREPARANDO, loaded.getStatus());
        assertEquals(now.toInstant(), loaded.getAcceptedAt().toInstant());
        // Aceptar no fija la espera de repartidor: esa corre solo cuando el pedido queda listo para retiro.
        assertNull(loaded.getCourierDueAt());
        // Una segunda aceptacion (o una carrera) no encuentra el pedido en Nuevo pedido.
        assertFalse(orders.acceptByStore(pedido.getId(), OrderStatus.NUEVO_PEDIDO, OrderStatus.PREPARANDO, now, null));
    }

    @Test
    void dejarElPedidoListoFijaLaEsperaDeRepartidorYConservaLaHoraDeToma() {
        // El pedido se paga (la tienda lo toma) y mas tarde queda listo para retiro.
        Order pedido = order(OrderStatus.PENDIENTE_PAGO, null, null, null);
        assertTrue(orders.confirmPayment(pedido.getId(), now.minusMinutes(30)));
        assertNull(orders.findById(pedido.getId()).orElseThrow().getCourierDueAt());

        assertTrue(orders.markReadyForPickup(pedido.getId(), OrderStatus.PREPARANDO, now, now.plusMinutes(20)));

        Order loaded = orders.findById(pedido.getId()).orElseThrow();
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, loaded.getStatus());
        assertEquals(now.minusMinutes(30).toInstant(), loaded.getAcceptedAt().toInstant());
        assertEquals(now.plusMinutes(20).toInstant(), loaded.getCourierDueAt().toInstant());
        // Si el pedido ya no estaba en Preparando (carrera), no se mueve ni se pisa el plazo.
        assertFalse(orders.markReadyForPickup(pedido.getId(), OrderStatus.PREPARANDO, now, now.plusMinutes(99)));
        assertEquals(now.plusMinutes(20).toInstant(),
                orders.findById(pedido.getId()).orElseThrow().getCourierDueAt().toInstant());
    }

    @Test
    void unPedidoQueSigueEnPreparandoNuncaVencePorFaltaDeRepartidor() {
        Order cocinando = order(OrderStatus.PREPARANDO, null, null, null);

        assertFalse(ids(orders.findCourierWaitOverdue(now.plusDays(7), 100)).contains(cocinando.getId()));
    }

    // ------------------------- sin repartidor -------------------------

    @Test
    void soloSeEncuentranLosListosParaRetiroSinRepartidorConLaEsperaVencida() {
        UUID repartidor = IdConverter.toUuid(9L);
        Order sinRepartidor = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), null);
        Order preparando = order(OrderStatus.PREPARANDO, null, now.minusMinutes(3), null);
        Order conRepartidor = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), repartidor);
        Order enPlazo = order(OrderStatus.LISTO_PARA_RETIRO, null, now.plusMinutes(5), null);
        Order nuevo = order(OrderStatus.NUEVO_PEDIDO, null, now.minusMinutes(1), null);
        Order retirado = order(OrderStatus.RETIRADO, null, now.minusMinutes(1), repartidor);

        List<UUID> found = ids(orders.findCourierWaitOverdue(now, 100));

        assertTrue(found.contains(sinRepartidor.getId()));
        // En Preparando no corre la espera de repartidor: el repartidor solo puede tomar el pedido cuando esta listo.
        assertFalse(found.contains(preparando.getId()));
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
        Order pedido = order(OrderStatus.LISTO_PARA_RETIRO, null, now.minusMinutes(1), null);

        assertTrue(orders.cancelUnattended(pedido.getId(), OrderStatus.LISTO_PARA_RETIRO, CancellationReason.NO_COURIER, now));

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
