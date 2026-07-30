package com.flashdrop.observability.error;

/**
 * Formato de error uniforme para TODO el sistema. Es el "idioma común"
 * que consume el dashboard de observabilidad. Compartido por todos los
 * servicios vía shared-observability.
 */
public record ApiError(String code, String service, String traceId, String message) {
}
