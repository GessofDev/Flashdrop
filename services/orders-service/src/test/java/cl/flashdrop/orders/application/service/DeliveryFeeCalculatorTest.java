package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryQuote;
import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.model.Vehicle;
import cl.flashdrop.orders.domain.port.DeliveryTariffRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.geo.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryFeeCalculatorTest {

    private static final BigDecimal FALLBACK = new BigDecimal("2500");

    private DeliveryTariffRepositoryPort tariffs;
    private DeliveryFeeCalculator calculator;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static DeliveryTariff tariff() {
        return new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, 4, Vehicle.BICI, bd("500")),
                        new DeliveryTariff.DimensionRange(5, null, Vehicle.AUTO, bd("2000"))),
                List.of(new DeliveryTariff.DistanceRange(bd("0"), bd("2"), bd("1000")),
                        new DeliveryTariff.DistanceRange(bd("2"), null, bd("2500"))));
    }

    private static RestaurantInfo restaurantAt(String lat, String lon) {
        return RestaurantInfo.builder().restaurantId(UUID.randomUUID()).name("Local").address("Calle 1")
                .latitude(lat == null ? null : bd(lat)).longitude(lon == null ? null : bd(lon))
                .deliveryRadiusKm(bd("10")).build();
    }

    private static DeliveryFeeCalculator.Line line(Integer dimension, int qty) {
        return new DeliveryFeeCalculator.Line(ProductInfo.builder().id(UUID.randomUUID())
                .price(bd("1000")).dimension(dimension).build(), qty);
    }

    @BeforeEach
    void setUp() {
        tariffs = mock(DeliveryTariffRepositoryPort.class);
        when(tariffs.find()).thenReturn(tariff());
        calculator = new DeliveryFeeCalculator(tariffs, new HaversineDistanceCalculator(), FALLBACK, 1);
    }

    @Test
    void calculate_missingDestination_rejectsInsteadOfBypassingCoverage() {
        assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(3, 1)), restaurantAt("-33.45", "-70.66"), null, null));
    }

    @Test
    void sinRangosCargadosSeCobraElEnvioDeRespaldo() {
        when(tariffs.find()).thenReturn(DeliveryTariff.empty());
        DeliveryQuote q = calculator.calculate(List.of(line(3, 1)), restaurantAt("-33.45", "-70.66"),
                bd("-33.40"), bd("-70.60"));
        assertTrue(q.defaultFee());
        assertEquals(FALLBACK, q.deliveryFee());
    }

    @Test
    void conTarifaYCoordenadasSumaDimensionMasDistancia() {
        // ~1,1 km hacia el norte: rango 0-2 km ($1.000). Dimension 3 x 1 = 3: BICI ($500).
        DeliveryQuote q = calculator.calculate(List.of(line(3, 1)), restaurantAt("-33.4500", "-70.6600"),
                bd("-33.4400"), bd("-70.6600"));
        assertFalse(q.defaultFee());
        assertEquals(Vehicle.BICI, q.vehicle());
        assertEquals(new BigDecimal("1500"), q.deliveryFee());
        assertEquals(new BigDecimal("1.11"), q.distanceKm());
    }

    @Test
    void laDimensionDelPedidoSumaCantidadPorDimensionYUsaLaDeRespaldoSiFalta() {
        // 2 x 3 + 1 x (sin dato = 1) = 7 -> AUTO
        DeliveryQuote q = calculator.calculate(List.of(line(3, 2), line(null, 1)),
                restaurantAt("-33.4500", "-70.6600"), bd("-33.4400"), bd("-70.6600"));
        assertEquals(7, q.dimension());
        assertEquals(Vehicle.AUTO, q.vehicle());
    }

    @Test
    void localSinPinConTarifaActivaDaUnErrorClaro() {
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(3, 1)), restaurantAt(null, null), bd("-33.44"), bd("-70.66")));
        assertTrue(ex.getMessage().contains("no tiene ubicacion"));
    }

    @Test
    void latitudSinLongitudSeRechaza() {
        assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(3, 1)), restaurantAt("-33.45", "-70.66"), bd("-33.44"), null));
    }

    @Test
    void calculate_outsideRadius_rejectsEvenWithoutTariff() {
        when(tariffs.find()).thenReturn(DeliveryTariff.empty());
        OrderDomainException error = assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(1, 1)), restaurantAt("-33.45", "-70.66"), bd("-34"), bd("-70.66")));
        assertTrue(error.getMessage().contains("fuera de su radio"));
    }

    @Test
    void calculate_missingRadius_rejects() {
        RestaurantInfo unconfigured = RestaurantInfo.builder().latitude(bd("-33.45"))
                .longitude(bd("-70.66")).build();
        assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(1, 1)), unconfigured, bd("-33.44"), bd("-70.66")));
    }

    @Test
    void calculate_boundary_includesRoundedDistance() {
        RestaurantInfo boundary = RestaurantInfo.builder().latitude(bd("-33.45"))
                .longitude(bd("-70.66")).deliveryRadiusKm(bd("1.11")).build();
        assertEquals(bd("1.11"), calculator.calculate(List.of(line(1, 1)), boundary,
                bd("-33.44"), bd("-70.66")).distanceKm());
    }

    @Test
    void calculate_invalidCoordinates_rejects() {
        assertThrows(OrderDomainException.class, () -> calculator.calculate(
                List.of(line(1, 1)), restaurantAt("-33.45", "-70.66"), bd("91"), bd("-70.66")));
    }
}
