package com.flashdrop.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;
import com.flashdrop.catalog.domain.valueobjects.DeliveryCoverage;
import org.junit.jupiter.api.Test;

class DeliveryCoverageTest {
    private Restaurant restaurant(String radius) {
        return new Restaurant(1L, 1L, "Local", "Calle", LocalDateTime.now(), true, List.of(),
                new BigDecimal("-33.45"), new BigDecimal("-70.66"),
                radius == null ? null : new BigDecimal(radius));
    }

    @Test
    void covers_insideAndOutside_filtersUsingConfiguredRadius() {
        assertTrue(DeliveryCoverage.covers(restaurant("5"),
                new Coordinates(new BigDecimal("-33.44"), new BigDecimal("-70.66"))));
        assertFalse(DeliveryCoverage.covers(restaurant("5"),
                new Coordinates(new BigDecimal("-33.55"), new BigDecimal("-70.66"))));
    }

    @Test
    void covers_boundary_includesSamePrecisionAsOrders() {
        assertTrue(DeliveryCoverage.covers(restaurant("1.11"),
                new Coordinates(new BigDecimal("-33.44"), new BigDecimal("-70.66"))));
    }

    @Test
    void covers_unconfigured_failsClosed() {
        Coordinates destination = new Coordinates(new BigDecimal("-33.44"), new BigDecimal("-70.66"));
        assertFalse(DeliveryCoverage.covers(restaurant(null), destination));
        assertFalse(DeliveryCoverage.covers(restaurant("0.5"), destination));
        assertFalse(DeliveryCoverage.covers(restaurant("51"), destination));
        assertFalse(DeliveryCoverage.covers(new Restaurant(1L, 1L, "Local", "Calle", LocalDateTime.now()), destination));
    }

    @Test
    void covers_radiusChanged_appliesNewRangeWithoutChangingDestination() {
        Coordinates destination = new Coordinates(new BigDecimal("-33.51"), new BigDecimal("-70.66"));
        assertFalse(DeliveryCoverage.covers(restaurant("5"), destination));
        assertTrue(DeliveryCoverage.covers(restaurant("10"), destination));
    }
}
