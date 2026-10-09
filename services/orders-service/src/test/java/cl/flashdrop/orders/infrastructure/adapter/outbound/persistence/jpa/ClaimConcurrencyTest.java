package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Punto 4 (Javier): varios repartidores intentan tomar el mismo pedido a la vez, cada uno en su
 * propia transacción y conexión sobre PostgreSQL real. Debe ganar exactamente uno.
 *
 * <p>Sin transacción de prueba (NOT_SUPPORTED): los datos se confirman de verdad, así que se
 * limpian al final.</p>
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClaimConcurrencyTest extends PostgresIntegrationTestSupport {

    @Autowired
    private SpringDataOrderRepository orderRepository;
    @Autowired
    private SpringDataClientRepository clientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void manyDeliveriesClaimingTheSameOrder_exactlyOneWins() throws Exception {
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()).createdAt(OffsetDateTime.now()).build());
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .clientId(client.getId()).restaurantId(7L).status("Listo para retiro")
                .address("Av. Test 1").subtotal(BigDecimal.valueOf(1000)).deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(3500)).paymentMethod("Tarjeta").createdAt(OffsetDateTime.now())
                .build());
        try {
            int contenders = 8;
            ExecutorService pool = Executors.newFixedThreadPool(contenders);
            CountDownLatch startTogether = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 1; i <= contenders; i++) {
                long deliveryId = 100L + i;
                Callable<Integer> claim = () -> {
                    startTogether.await();
                    return tx.execute(status -> orderRepository.claimUnassigned(deliveryId, List.of(order.getId())));
                };
                results.add(pool.submit(claim));
            }
            startTogether.countDown();
            int totalClaimed = 0;
            for (Future<Integer> result : results) {
                totalClaimed += result.get();
            }
            pool.shutdown();

            assertEquals(1, totalClaimed, "Solo un repartidor debe quedarse con el pedido");
            Long winner = orderRepository.findById(order.getId()).orElseThrow().getDeliveryId();
            assertEquals(true, winner != null && winner > 100L);
        } finally {
            orderRepository.deleteById(order.getId());
            clientRepository.deleteById(client.getId());
        }
    }
}
