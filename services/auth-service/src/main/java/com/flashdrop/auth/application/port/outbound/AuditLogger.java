package com.flashdrop.auth.application.port.outbound;

/**
 * S-17: registro de auditoría de acciones sensibles.
 */
public interface AuditLogger {

    void record(AuditEvent event);

    /**
     * @param action  que pasó
     * @param actor   quien lo hizo: el sujeto de la accion cuando es de
     *                autoservicio (LOGIN, REGISTER) y el principal que la
     *                ejecuto cuando es de terceros (ASSIGN_ROLES)
     * @param result  SUCCESS o FAIL
     * @param ip      origen de la peticion, null si el flujo no la conoce
     * @param target  sobre quien o sobre que se actuo; null solo cuando el
     *                actor no identifica al destinatario
     */
    record AuditEvent(String action, String actor, String result, String ip, String target) {

        /**
         * Para los flujos de autoservicio, donde el actor es tambien el
         * destinatario: el login y el registro se hacen sobre uno mismo.
         */
        public AuditEvent(String action, String actor, String result, String ip) {
            this(action, actor, result, ip, actor);
        }
    }
}
