package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.application.dto.DeliveryCodeVerification;
import cl.flashdrop.orders.application.dto.DeliveryCodeVerification.Outcome;
import cl.flashdrop.orders.application.usecase.VerifyDeliveryCodeUseCase;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.code.HmacDeliveryCodeAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderDeliveryCodeAttemptRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderItemRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Varios intentos de código incorrecto a la vez sobre el mismo pedido, cada uno en su propia
 * transacción y conexión sobre PostgreSQL real. El bloqueo de fila debe serializarlos: nunca se
 * pueden registrar más fallos que los permitidos (2 códigos x 3 intentos = 6).
 *
 * <p>Sin transacción de prueba (NOT_SUPPORTED): los datos se confirman de verdad, así que se
 * limpian al final.</p>
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryCodeConcurrencyTest extends PostgresIntegrationTestSupport {

    private static final long DELIVERY_RAW_ID = 55L;

    @Autowired
    private SpringDataOrderRepository orderRepository;
    @Autowired
    private SpringDataOrderItemRepository orderItemRepository;
    @Autowired
    private SpringDataOrderDeliveryCodeAttemptRepository attemptRepository;
    @Autowired
    private SpringDataClientRepository clientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void manyWrongCodesAtOnce_neverExceedTheAllowedAttempts() throws Exception {
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()).createdAt(OffsetDateTime.now()).build());
        OrderEntity order = orderRepository.save(OrderEntity.builder()
                .clientId(client.getId()).restaurantId(7L).deliveryId(DELIVERY_RAW_ID).status("Retirado")
                .address("Av. Test 1").subtotal(BigDecimal.valueOf(1000)).deliveryFee(BigDecimal.valueOf(2500))
                .total(BigDecimal.valueOf(3500)).paymentMethod("Tarjeta").createdAt(OffsetDateTime.now())
                .receptionMode("SEGURO").build());
        try {
            UUID courierUser = IdConverter.toUuid(900L);
            DeliveryPort deliveryPort = mock(DeliveryPort.class);
            when(deliveryPort.findDeliveryIdByUserId(courierUser))
                    .thenReturn(Optional.of(IdConverter.toUuid(DELIVERY_RAW_ID)));
            ClientPort clientPort = mock(ClientPort.class);
            when(clientPort.findClientIdByUserId(any())).thenReturn(Optional.empty());

            VerifyDeliveryCodeUseCase useCase = new VerifyDeliveryCodeUseCase(
                    new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository, attemptRepository),
                    deliveryPort, clientPort, new HmacDeliveryCodeAdapter("concurrency-secret"),
                    mock(EventPublisherPort.class));
            ReflectionTestUtils.setField(useCase, "statusUpdatedRoutingKey", "order.status.updated");

            int contenders = 8;
            ExecutorService pool = Executors.newFixedThreadPool(contenders);
            CountDownLatch startTogether = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            List<Future<DeliveryCodeVerification>> results = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await();
                    return tx.execute(status -> useCase.execute(
                            IdConverter.toUuid(order.getId()), "999999", Set.of(Role.REPARTIDOR), courierUser));
                }));
            }
            startTogether.countDown();
            int wrong = 0, renewed = 0, locked = 0;
            for (Future<DeliveryCodeVerification> result : results) {
                Outcome outcome = result.get().outcome();
                if (outcome == Outcome.WRONG_CODE) wrong++;
                if (outcome == Outcome.CODE_RENEWED) renewed++;
                if (outcome == Outcome.LOCKED) locked++;
            }
            pool.shutdown();

            // Serializados: 4 fallos simples, 1 renovacion y 3 bloqueos (el 6to fallo bloquea).
            assertEquals(4, wrong);
            assertEquals(1, renewed);
            assertEquals(3, locked);
            assertEquals(6, attemptRepository.countByOrderId(order.getId()),
                    "Solo se registran los 6 intentos permitidos; los bloqueados no cuentan");
            OrderEntity finalState = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(2, finalState.getDeliveryCodeVersion());
            assertEquals(3, finalState.getDeliveryCodeAttempts());
            assertEquals("Retirado", finalState.getStatus());
            assertTrue(finalState.getDeliveryCodeVerifiedAt() == null);
        } finally {
            attemptRepository.deleteAll(attemptRepository.findAll().stream()
                    .filter(a -> a.getOrderId().equals(order.getId())).toList());
            orderRepository.deleteById(order.getId());
            clientRepository.deleteById(client.getId());
        }
    }
}
