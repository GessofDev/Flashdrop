package com.flashdrop.observability.tracing;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generaTraceIdCuandoNoLlegaNinguno() throws Exception {
        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        final String[] duranteRequest = new String[1];

        filter.doFilter(req, res, (rq, rs) -> duranteRequest[0] = MDC.get("traceId"));

        assertNotNull(duranteRequest[0], "el traceId debe existir en el MDC durante el request");
        assertEquals(16, res.getHeader("X-Trace-Id").length());
        assertNull(MDC.get("traceId"), "el MDC debe limpiarse tras el request");
    }

    @Test
    void reutilizaElTraceIdEntranteParaMantenerLaTraza() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", "trace-desde-gateway");
        var res = new MockHttpServletResponse();

        filter.doFilter(req, res, (FilterChain) (rq, rs) -> {});

        assertEquals("trace-desde-gateway", res.getHeader("X-Trace-Id"));
    }
}
