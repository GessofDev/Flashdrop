package com.flashdrop.auth.infrastructure.adapter.outbound.audit;

import com.flashdrop.auth.application.port.outbound.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * S-17: escribe eventos de auditoría al logger "AUDIT". Con el encoder JSON
 * de shared-observability, cada evento sale como línea estructurada con trace_id.
 */
@Component
public class Slf4jAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @Override
    public void record(AuditEvent event) {
        log.info("action={} actor={} result={} ip={}",
                event.action(), event.actor(), event.result(), event.ip());
    }
}
