package cl.flashdrop.orders.infrastructure.adapter.outbound;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdConverterTest {

    @Test
    void shouldRoundTripExternalReferenceIds() {
        // IDs externos (long) referenciados por Orders -> domain UUID -> back to Long.
        long[] externalRefs = {1L, 2L, 7L, 10L, 501L, 1L << 32, (1L << 40) + 5};
        for (long ref : externalRefs) {
            UUID uuid = IdConverter.toUuid(ref);
            assertEquals(ref, IdConverter.toLong(uuid));
            assertEquals(ref, IdConverter.toLongParam(uuid));
        }
    }

    @Test
    void shouldConvertNullSafely() {
        assertEquals(0L, IdConverter.toLong(null));
        assertNotNull(IdConverter.toUuid(0L));
    }

    @Test
    void shouldMapOrderAndItemIds() {
        // orders.id / order_items.id / client.id son PK propias (Long externo) del Orders DB.
        long orderId = 501L;
        UUID uuid = IdConverter.toUuid(orderId);
        assertEquals(orderId, IdConverter.toLong(uuid));
    }
}
