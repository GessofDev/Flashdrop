package com.flashdrop.auth.application.port.outbound;

/**
 * S-17: registro de auditoría de acciones sensibles.
 */
public interface AuditLogger {

    void record(AuditEvent event);

    record AuditEvent(String action, String actor, String result, String ip) {}
}
