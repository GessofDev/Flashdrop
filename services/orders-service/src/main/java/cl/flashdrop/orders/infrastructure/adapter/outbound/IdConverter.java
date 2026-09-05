package cl.flashdrop.orders.infrastructure.adapter.outbound;

import java.util.UUID;

/**
 * Conversor UUID (dominio) ↔ Long (ids externos de Supabase).
 *
 * <p>La tabla {@code client} propia de Orders usa UUID nativo; el resto de los servicios
 * usan {@code Long}. La conversión se mantiene acotada al layer de infraestructura.</p>
 */
public final class IdConverter {

    private static final long MASK = 0x7FFFFFFFFFFFFFFFL;

    private IdConverter() {
    }

    public static long toLong(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        return uuid.getLeastSignificantBits() & MASK;
    }

    public static UUID toUuid(long id) {
        return new UUID(0L, id);
    }

    public static long toLongParam(UUID uuid) {
        return toLong(uuid);
    }
}
