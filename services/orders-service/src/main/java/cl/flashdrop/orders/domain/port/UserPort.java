package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.UserInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de usuario expuesto por Auth (C-4).
 *
 * <p>Orders consume esta API interna en lugar de consultar directamente la tabla {@code users}
 * de Supabase.</p>
 */
public interface UserPort {
    Optional<UserInfo> findUserById(UUID userId);
}
