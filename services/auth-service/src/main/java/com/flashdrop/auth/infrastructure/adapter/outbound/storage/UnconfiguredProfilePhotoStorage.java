package com.flashdrop.auth.infrastructure.adapter.outbound.storage;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flashdrop.auth.application.port.outbound.ProfilePhotoStorage;
import com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException;

/**
 * Se usa cuando {@code AUTH_PHOTO_STORAGE_ENABLED} no esta encendido (desarrollo
 * local, tests). Auth arranca igual: subir o leer una foto responde 503, y borrar
 * no hace nada porque no hay nada guardado (la eliminacion de cuenta sigue funcionando).
 */
public class UnconfiguredProfilePhotoStorage implements ProfilePhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredProfilePhotoStorage.class);
    private static final String MESSAGE = "El almacenamiento de fotos de perfil no está disponible";

    public UnconfiguredProfilePhotoStorage() {
        log.warn("Fotos de perfil desactivadas: falta AUTH_PHOTO_STORAGE_ENABLED=true y la configuracion S3");
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        throw new PhotoStorageUnavailableException(MESSAGE);
    }

    @Override
    public Optional<byte[]> get(String key) {
        throw new PhotoStorageUnavailableException(MESSAGE);
    }

    @Override
    public void delete(String key) {
        // Nada que borrar.
    }

    @Override
    public void deleteAll(String prefix) {
        // Nada que borrar.
    }
}
