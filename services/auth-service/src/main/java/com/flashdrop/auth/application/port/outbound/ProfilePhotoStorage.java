package com.flashdrop.auth.application.port.outbound;

import java.util.Optional;

/**
 * Almacenamiento de las fotos de perfil (bucket privado de Floci en el servidor).
 *
 * <p>Las fallas del almacenamiento se informan con
 * {@link com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException}.
 */
public interface ProfilePhotoStorage {

    void put(String key, byte[] content, String contentType);

    /** Vacio si el objeto no existe. */
    Optional<byte[]> get(String key);

    /** Idempotente: borrar un objeto que no existe no es un error. */
    void delete(String key);

    /** Borra todo lo que esta bajo el prefijo. Idempotente; lo usa la eliminacion de cuenta (F5). */
    void deleteAll(String prefix);
}
