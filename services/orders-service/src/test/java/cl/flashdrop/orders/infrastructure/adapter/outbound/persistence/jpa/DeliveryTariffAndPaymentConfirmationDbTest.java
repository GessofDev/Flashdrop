package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.model.Vehicle;
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
 * Tarifa de envio (V12) y confirmacion de pago / coordenadas de destino (V11, V13) contra
 * PostgreSQL real con las migraciones reales.
 */
class DeliveryTariffAndPaymentConfirmationDbTest extends PostgresIntegrationTestSupport {

    @Autowired private SpringDataOrderRepository orderRepository;
    @Autowired private SpringDataOrderItemRepository orderItemRepository;
    @Autowired private SpringDataOrderDeliveryCodeAttemptRepository attemptRepository;
    @Autowired private SpringDataClientRepository clientRepository;
    @Autowired private SpringDataDeliveryTariffDimensionRepository dimensionRepository;
    @Autowired private SpringDataDeliveryTariffDistanceRepository distanceRepository;

    private JpaOrderRepositoryAdapter orders;
    private JpaDeliveryTariffRepositoryAdapter tariffs;
    private UUID clientId;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static DeliveryTariff tariff(String lastDistancePrice) {
        return new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, 4, Vehicle.BICI, bd("500")),
                        new DeliveryTariff.DimensionRange(5, null, Vehicle.AUTO, bd("2000"))),
                List.of(new DeliveryTariff.DistanceRange(bd("0"), bd("2.5"), bd("1000")),
                        new DeliveryTariff.DistanceRange(bd("2.5"), null, bd(lastDistancePrice))));
    }

    @BeforeEach
    void setUp() {
        orders = new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository, attemptRepository);
        tariffs = new JpaDeliveryTariffRepositoryAdapter(dimensionRepository, distanceRepository);
        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()).createdAt(OffsetDateTime.now()).build());
        clientId = IdConverter.toUuid(client.getId());
    }

    private Order pendingOrder() {
        return orders.save(Order.builder().clientId(clientId).restaurantId(IdConverter.toUuid(7L))
                .status(OrderStatus.PENDIENTE_PAGO).address("Calle 1")
                .deliveryLatitude(bd("-33.440000")).deliveryLongitude(bd("-70.660000"))
                .distanceKm(bd("4.25")).estimatedMinutes(25)
                .subtotal(BigDecimal.valueOf(7500)).deliveryFee(BigDecimal.valueOf(2500)).total(BigDecimal.valueOf(10000))
                .paymentMethod(PaymentMethod.TARJETA).items(List.of()).build());
    }

    @Test
    void sinRangosCargadosLaTarifaVieneVacia() {
        assertTrue(tariffs.find().isEmpty());
    }

    @Test
    void laTarifaSeGuardaOrdenadaYSeReemplazaCompleta() {
        tariffs.replace(tariff("2500"), UUID.randomUUID());
        DeliveryTariff saved = tariffs.find();
        assertEquals(2, saved.dimensionRanges().size());
        assertEquals(Vehicle.AUTO, saved.dimensionRanges().get(1).vehicle());
        assertNull(saved.dimensionRanges().get(1).maxDimension());
        assertEquals(0, bd("2.5").compareTo(saved.distanceRanges().get(1).minKm()));

        tariffs.replace(tariff("3000"), UUID.randomUUID());
        assertEquals(2, tariffs.find().distanceRanges().size());
        assertEquals(0, bd("3000").compareTo(tariffs.find().distanceRanges().get(1).price()));
        assertEquals(0, bd("3500").compareTo(tariffs.find().quote(1, bd("3")).deliveryFee()));
    }

    @Test
    void conListasVaciasSeVuelveAlEnvioDeRespaldo() {
        tariffs.replace(tariff("2500"), UUID.randomUUID());
        tariffs.replace(DeliveryTariff.empty(), UUID.randomUUID());
        assertTrue(tariffs.find().isEmpty());
    }

    @Test
    void unaTarifaConHuecosNoSeGuardaYNoDestruyeLaVigente() {
        tariffs.replace(tariff("2500"), UUID.randomUUID());
        DeliveryTariff conHueco = new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, 4, Vehicle.BICI, bd("1")),
                        new DeliveryTariff.DimensionRange(6, null, Vehicle.AUTO, bd("2"))),
                tariff("1").distanceRanges());

        assertThrows(OrderDomainException.class, () -> tariffs.replace(conHueco, UUID.randomUUID()));
        assertEquals(2, tariffs.find().dimensionRanges().size());
        assertEquals(0, bd("500").compareTo(tariffs.find().dimensionRanges().get(0).price()));
    }

    @Test
    void elPedidoGuardaDestinoDistanciaYTiempoEstimado() {
        Order saved = pendingOrder();
        Order loaded = orders.findById(saved.getId()).orElseThrow();
        assertEquals(0, bd("-33.440000").compareTo(loaded.getDeliveryLatitude()));
        assertEquals(0, bd("-70.660000").compareTo(loaded.getDeliveryLongitude()));
        assertEquals(0, bd("4.25").compareTo(loaded.getDistanceKm()));
        assertEquals(25, loaded.getEstimatedMinutes());
    }

    @Test
    void confirmarElPagoHaceQueLaTiendaTomeElPedidoUnaSolaVezSinFijarAunLaEsperaDeRepartidor() {
        Order saved = pendingOrder();
        OffsetDateTime now = OffsetDateTime.now().withNano(0);

        assertTrue(orders.confirmPayment(saved.getId(), now));
        Order loaded = orders.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.PREPARANDO, loaded.getStatus());
        assertEquals(now.toInstant(), loaded.getAcceptedAt().toInstant());
        // La espera de repartidor empieza cuando el pedido queda listo para retiro, no al pagar.
        assertNull(loaded.getCourierDueAt());
        assertNull(loaded.getStoreResponseDueAt());

        // Un segundo aviso (o una carrera) ya no encuentra el pedido pendiente y no mueve la hora de toma.
        assertFalse(orders.confirmPayment(saved.getId(), now.plusMinutes(5)));
        assertEquals(now.toInstant(), orders.findById(saved.getId()).orElseThrow().getAcceptedAt().toInstant());
    }
}
