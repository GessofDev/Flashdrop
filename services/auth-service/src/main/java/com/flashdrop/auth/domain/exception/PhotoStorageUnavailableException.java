package com.flashdrop.auth.domain.exception;

/**
 * El almacenamiento de fotos (bucket de Floci) no esta configurado o no responde.
 * Se mapea a HTTP 503; la foto anterior del usuario se mantiene.
 */
public class PhotoStorageUnavailableException extends DomainException {
    public PhotoStorageUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", message);
    }

    public PhotoStorageUnavailableException(String message, Throwable cause) {
        super("SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}
