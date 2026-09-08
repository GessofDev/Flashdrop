package cl.flashdrop.orders.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Representa un cliente de Orders (propietario del negocio).
 *
 * <p>Combina la fila de la tabla {@code client} (Orders) con los datos de usuario
 * provistos por Auth (C-4) mediante {@code UserPort}, evitando el acceso directo
 * a la tabla {@code users}.</p>
 */
@Value
@AllArgsConstructor
@Builder
public class ClientInfo {

    UUID clientId;
    String fullName;
    String email;
    String phone;

    /** Nombre descriptivo usado en las respuestas de la API. */
    public String fullName() {
        return fullName != null ? fullName : "Cliente";
    }
}
