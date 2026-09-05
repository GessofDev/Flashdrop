package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Utilidades de mapeo de errores para los clientes HTTP entre servicios internos.
 */
public final class InternalHttpSupport {

    private InternalHttpSupport() {
    }

    public static ExternalServiceException httpError(String service, HttpStatusCodeException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return new ExternalServiceException(status, service + " error: " + e.getMessage(), e);
    }

    /**
     * GAP (auditoría 2026-09-04): MIGRATION_PLAN.md §10 define 503/SERVICE_UNAVAILABLE
     * explícitamente para "servicio dependiente caído o timeout" — antes se devolvía 502
     * (BAD_GATEWAY), que no aparece en la tabla de códigos del plan.
     */
    public static ExternalServiceException connectionFailure(String service, ResourceAccessException e) {
        return new ExternalServiceException(HttpStatus.SERVICE_UNAVAILABLE, service + " no disponible", e);
    }
}
