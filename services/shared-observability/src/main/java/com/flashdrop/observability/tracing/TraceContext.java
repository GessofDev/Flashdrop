package com.flashdrop.observability.tracing;

import org.slf4j.MDC;

/**
 * Acceso al trace_id vigente. Lo usan los GlobalExceptionHandler para
 * incluirlo en la respuesta de error.
 */
public final class TraceContext {

    private TraceContext() {}

    public static String currentTraceId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
