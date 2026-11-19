package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryTariffTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static DeliveryTariff valid() {
        return new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, 4, Vehicle.BICI, bd("500")),
                        new DeliveryTariff.DimensionRange(5, 8, Vehicle.MOTO, bd("1000")),
                        new DeliveryTariff.DimensionRange(9, null, Vehicle.AUTO, bd("2500"))),
                List.of(new DeliveryTariff.DistanceRange(bd("0"), bd("2"), bd("1000")),
                        new DeliveryTariff.DistanceRange(bd("2"), bd("5"), bd("1800")),
                        new DeliveryTariff.DistanceRange(bd("5"), null, bd("3000"))));
    }

    @Test
    void elEnvioEsLaSumaDelCostoPorDimensionYPorDistancia() {
        DeliveryQuote q = valid().quote(6, bd("3.40"));
        assertEquals(Vehicle.MOTO, q.vehicle());
        assertEquals(bd("1000"), q.dimensionCost());
        assertEquals(bd("1800"), q.distanceCost());
        assertEquals(bd("2800"), q.deliveryFee());
        assertFalse(q.defaultFee());
    }

    @Test
    void mayorDimensionAsignaAutoYElUltimoRangoEsAbierto() {
        DeliveryQuote q = valid().quote(40, bd("12"));
        assertEquals(Vehicle.AUTO, q.vehicle());
        assertEquals(bd("5500"), q.deliveryFee());
    }

    @Test
    void elLimiteSuperiorDeDistanciaNoSeIncluyeYElDeDimensionSi() {
        assertEquals(bd("1800"), valid().quote(1, bd("2")).distanceCost());
        assertEquals(Vehicle.BICI, valid().quote(4, bd("1")).vehicle());
        assertEquals(Vehicle.MOTO, valid().quote(5, bd("1")).vehicle());
    }

    @Test
    void tarifaValidaPasaLaValidacionYLaVaciaTambien() {
        assertDoesNotThrow(valid()::validate);
        assertDoesNotThrow(DeliveryTariff.empty()::validate);
        assertTrue(DeliveryTariff.empty().isEmpty());
    }

    @Test
    void rechazaHuecosSolapesYRangosSinCerrar() {
        // hueco en dimension (falta el 5)
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, 4, Vehicle.BICI, bd("1")),
                        new DeliveryTariff.DimensionRange(6, null, Vehicle.AUTO, bd("2"))),
                valid().distanceRanges()).validate());
        // solape en distancia
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(valid().dimensionRanges(),
                List.of(new DeliveryTariff.DistanceRange(bd("0"), bd("3"), bd("1")),
                        new DeliveryTariff.DistanceRange(bd("2"), null, bd("2")))).validate());
        // no parte en 0
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(1, null, Vehicle.AUTO, bd("1"))),
                valid().distanceRanges()).validate());
        // el ultimo rango no queda abierto
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(valid().dimensionRanges(),
                List.of(new DeliveryTariff.DistanceRange(bd("0"), bd("5"), bd("1")))).validate());
    }

    @Test
    void rechazaPreciosNegativosYTablasIncompletas() {
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(
                List.of(new DeliveryTariff.DimensionRange(0, null, Vehicle.AUTO, bd("-1"))),
                valid().distanceRanges()).validate());
        assertThrows(OrderDomainException.class, () -> new DeliveryTariff(
                valid().dimensionRanges(), List.of()).validate());
    }
}
