package cl.flashdrop.orders.infrastructure.exception;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.infrastructure.api.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cubre ORD-F4: el contrato de error debe ser {@code { status, error, message } }
 * (MIGRATION_PLAN.md §10), no {@code { error, message, timestamp } } (formato legado
 * "compatible con Node.js" que tenía el handler antes de esta corrección).
 *
 * <p>También cubre el bug de casing detectado en la auditoría: mensajes de dominio como
 * "Ya tienes pedidos en ruta..." (mayúscula inicial) deben mapear a 409/CONFLICT igual
 * que "ya fueron tomados..."/"Alguien tomo...".</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void domainException_conMensajeDeConflictoEnMayuscula_mapeaA409() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new OrderDomainException("Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
        assertEquals("CONFLICT", response.getBody().error());
    }

    @Test
    void domainException_conflictoDeConcurrencia_mapeaA409() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new OrderDomainException("Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista"));

        assertEquals(409, response.getBody().status());
        assertEquals("CONFLICT", response.getBody().error());
    }

    @Test
    void domainException_recursoNoEncontrado_mapeaA404() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new OrderDomainException("Uno o mas pedidos ya no estan disponibles"));

        assertEquals(404, response.getBody().status());
        assertEquals("NOT_FOUND", response.getBody().error());
    }

    @Test
    void domainException_sinPerfilDeRepartidor_mapeaA403() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new OrderDomainException("El usuario no tiene perfil de repartidor"));

        assertEquals(403, response.getBody().status());
        assertEquals("FORBIDDEN", response.getBody().error());
    }

    @Test
    void domainException_reglaGenerica_mapeaA400() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new OrderDomainException("Debes seleccionar entre 1 y 3 pedidos para tomar la ruta"));

        assertEquals(400, response.getBody().status());
        assertEquals("BAD_REQUEST", response.getBody().error());
    }

    @Test
    void externalServiceException_serviceUnavailable_mapeaCodigoDelPlan() {
        ResponseEntity<ErrorResponse> response = handler.handleExternalService(
                new ExternalServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Delivery no disponible"));

        // MIGRATION_PLAN.md §10: "servicio dependiente caído o timeout" -> 503.
        // InternalHttpSupport.connectionFailure() ya construye la excepción con este status
        // (corregido en la auditoría 2026-09-04; antes era 502 BAD_GATEWAY).
        assertEquals(503, response.getBody().status());
        assertEquals("SERVICE_UNAVAILABLE", response.getBody().error());
    }

    @Test
    void externalServiceException_upstreamBadGatewayLiteral_caeEnElBucketMasCercano() {
        // Si el servicio dependiente en sí devuelve un 502 (no un timeout/caído detectado
        // por Orders, sino la respuesta HTTP literal de, por ej., un proxy intermedio),
        // InternalHttpSupport.httpError() preserva ese status real; 502 no está en la
        // tabla de §10, así que el código "error" cae al bucket más cercano
        // (SERVICE_UNAVAILABLE) sin inventar un código no documentado por el plan.
        ResponseEntity<ErrorResponse> response = handler.handleExternalService(
                new ExternalServiceException(HttpStatus.BAD_GATEWAY, "Catalog error: 502 Bad Gateway"));

        assertEquals(502, response.getBody().status());
        assertEquals("SERVICE_UNAVAILABLE", response.getBody().error());
    }

    @Test
    void genericException_mapeaA500ConCodigoDelPlan() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new RuntimeException("boom"));

        assertEquals(500, response.getBody().status());
        assertEquals("INTERNAL_ERROR", response.getBody().error());
    }

    /**
     * GAP-03/GAP-04 (auditoría 2026-09-04): CurrentUserResolver y los controllers lanzan
     * AccessDeniedException cuando el usuario autenticado intenta operar con una identidad
     * que no es la suya. Sin este handler explícito, caería en handleGenericException
     * (500 INTERNAL_ERROR) en vez de 403 FORBIDDEN.
     */
    @Test
    void accessDeniedException_mapeaA403ConCodigoDelPlan() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new org.springframework.security.access.AccessDeniedException("No puedes consultar pedidos de otro usuario"));

        assertEquals(403, response.getBody().status());
        assertEquals("FORBIDDEN", response.getBody().error());
        assertEquals("No puedes consultar pedidos de otro usuario", response.getBody().message());
    }
}
