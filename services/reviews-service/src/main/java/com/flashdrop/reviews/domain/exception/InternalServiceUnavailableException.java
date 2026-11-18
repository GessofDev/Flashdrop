package com.flashdrop.reviews.domain.exception;

/**
 * Falló la consulta a un servicio interno (orders o catalog): timeout, error de
 * red o respuesta no 2xx. Se traduce a HTTP 503.
 *
 * <p>Degradación segura: reviews-service <b>nunca</b> valida contra datos
 * inventados. Si no puede saber el estado real de la orden, no califica.</p>
 */
public class InternalServiceUnavailableException extends RuntimeException {

    public InternalServiceUnavailableException(String service, String message, Throwable cause) {
        super("El servicio " + service + " no esta disponible: " + message, cause);
    }
}
