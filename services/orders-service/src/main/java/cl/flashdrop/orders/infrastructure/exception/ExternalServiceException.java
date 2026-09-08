package cl.flashdrop.orders.infrastructure.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción de infraestructura que envuelve fallos de los clientes HTTP
 * entre servicios (Catalog, Auth, Delivery).
 *
 * Carrega el código de estado HTTP del servicio externo para que el
 * {@link GlobalExceptionHandler} traduzca directamente al cliente en lugar
 * de convertir todo en 500.
 */
public class ExternalServiceException extends RuntimeException {

    private final HttpStatus status;

    public ExternalServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ExternalServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
