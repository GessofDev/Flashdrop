package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.ClientInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla propia {@code client} de Orders (Owners-only table).
 * Enriquece con datos básicos del usuario obtenidos vía Auth (C-4).
 *
 * <p>No accede a tablas externas como {@code users}.</p>
 */
public interface ClientPort {

    /**
     * Resuelve el id de perfil de cliente a partir del id de usuario en Auth.
     * Consulta la tabla {@code client} propia de Orders.
     */
    Optional<UUID> findClientIdByUserId(UUID userId);

    /**
     * Obtiene la información completa de un cliente por su id de perfil.
     */
    Optional<ClientInfo> findClientById(UUID clientId);
}
