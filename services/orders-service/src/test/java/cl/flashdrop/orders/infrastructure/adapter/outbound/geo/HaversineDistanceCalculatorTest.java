package cl.flashdrop.orders.infrastructure.adapter.outbound.geo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void mismoPuntoEsCeroKm() {
        BigDecimal km = calculator.kmBetween(new BigDecimal("-33.45"), new BigDecimal("-70.66"),
                new BigDecimal("-33.45"), new BigDecimal("-70.66"));
        assertEquals(0, km.compareTo(BigDecimal.ZERO));
    }

    @Test
    void unGradoDeLatitudSonUnos111Km() {
        BigDecimal km = calculator.kmBetween(new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("1"), new BigDecimal("0"));
        assertEquals(new BigDecimal("111.20"), km);
    }

    @Test
    void santiagoAValparaisoEstaCercaDe100Km() {
        BigDecimal km = calculator.kmBetween(new BigDecimal("-33.4489"), new BigDecimal("-70.6693"),
                new BigDecimal("-33.0472"), new BigDecimal("-71.6127"));
        assertTrue(km.doubleValue() > 95 && km.doubleValue() < 105, "km=" + km);
    }
}
