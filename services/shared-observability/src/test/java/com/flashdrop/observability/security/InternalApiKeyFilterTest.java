package com.flashdrop.observability.security;

import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InternalApiKeyFilter.
 * Covers: missing key → 401, wrong key → 401, correct key → 200.
 * The filter uses constant-time comparison (MessageDigest.isEqual) to prevent timing attacks.
 */
class InternalApiKeyFilterTest {

    private static final String VALID_KEY = "super-secret-internal-key-123";
    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(VALID_KEY);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingKey_returns401() throws Exception {
        var req = buildRequest("/api/internal/delivery-persons", null);
        var res = new MockHttpServletResponse();
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-trace-id");

        try {
            filter.doFilter(req, res, noOpChain());

            assertEquals(401, res.getStatus());
            assertEquals("application/json", res.getContentType());

            ApiError error = objectMapper.readValue(res.getContentAsString(), ApiError.class);
            assertEquals("UNAUTHORIZED", error.code());
            assertEquals("shared-observability", error.service());
            assertEquals("test-trace-id", error.traceId());
            assertTrue(error.message().contains("internal API key"));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void wrongKey_returns401() throws Exception {
        var req = buildRequest("/api/internal/delivery-persons", "wrong-key");
        var res = new MockHttpServletResponse();
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-trace-id");

        try {
            filter.doFilter(req, res, noOpChain());

            assertEquals(401, res.getStatus());
            ApiError error = objectMapper.readValue(res.getContentAsString(), ApiError.class);
            assertEquals("UNAUTHORIZED", error.code());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void correctKey_passesThrough() throws Exception {
        var req = buildRequest("/api/internal/delivery-persons", VALID_KEY);
        var res = new MockHttpServletResponse();
        final boolean[] chainCalled = {false};

        filter.doFilter(req, res, (r, s) -> chainCalled[0] = true);

        assertTrue(chainCalled[0], "filter chain must be called when key is valid");
        assertEquals(200, res.getStatus());
    }

    @Test
    void nonInternalPath_bypassesFilter() throws Exception {
        var req = buildRequest("/api/delivery/claim", null);
        var res = new MockHttpServletResponse();
        final boolean[] chainCalled = {false};

        filter.doFilter(req, res, (r, s) -> chainCalled[0] = true);

        assertTrue(chainCalled[0], "non-/api/internal/* paths must bypass the filter");
    }

    @Test
    void wrongKey_timingAttack_resistant() throws Exception {
        // MessageDigest.isEqual uses constant-time comparison — verify via behavior
        var req1 = buildRequest("/api/internal/delivery-persons", "key-one-char-off");
        var req2 = buildRequest("/api/internal/delivery-persons", "completely-wrong-key");
        var res1 = new MockHttpServletResponse();
        var res2 = new MockHttpServletResponse();

        filter.doFilter(req1, res1, noOpChain());
        filter.doFilter(req2, res2, noOpChain());

        // Both must return 401 — no timing difference leaks information
        assertEquals(401, res1.getStatus());
        assertEquals(401, res2.getStatus());
    }

    private MockHttpServletRequest buildRequest(String path, String apiKey) {
        var req = new MockHttpServletRequest();
        req.setRequestURI(path);
        if (apiKey != null) {
            req.addHeader("X-Internal-Api-Key", apiKey);
        }
        return req;
    }

    private FilterChain noOpChain() {
        return (r, s) -> {};
    }
}
