package cl.flashdrop.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private InternalApiKeyFilter filter;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception {
        filter = new InternalApiKeyFilter("dev-key");
        body = new StringWriter();
        // lenient: solo los tests de rechazo llegan a escribir el body de error.
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    @Test
    void shouldAllowWhenValidKey() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("dev-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldRejectWhenKeyMissing() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertRejectedWithForbiddenErrorBody();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenKeyIncorrect() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("wrong");

        filter.doFilterInternal(request, response, chain);

        assertRejectedWithForbiddenErrorBody();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotAffectPublicEndpoints() throws Exception {
        when(request.getServletPath()).thenReturn("/api/orders");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // Cubre D7 (Plan_ Servicio_delivery.txt): POST /api/internal/orders/claim debe quedar
    // protegido automáticamente por este mismo filtro (match por prefijo /api/internal/),
    // sin configuración adicional.

    @Test
    void shouldAllowInternalClaimEndpointWhenValidKey() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders/claim");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("dev-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldRejectInternalClaimEndpointWhenKeyMissing() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders/claim");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertRejectedWithForbiddenErrorBody();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectInternalClaimEndpointWhenKeyIncorrect() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders/claim");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("wrong");

        filter.doFilterInternal(request, response, chain);

        assertRejectedWithForbiddenErrorBody();
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Verifica el body real emitido por {@link ErrorResponseWriter} — no solo que se llamó
     * a alguna API de error, sino que el contrato MIGRATION_PLAN.md §10 se respeta:
     * {@code { "status": 403, "error": "FORBIDDEN", "message": "Invalid internal API key" } }.
     */
    private void assertRejectedWithForbiddenErrorBody() {
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String json = body.toString();
        assertTrue(json.contains("\"status\":403"), "body: " + json);
        assertTrue(json.contains("\"error\":\"FORBIDDEN\""), "body: " + json);
        assertTrue(json.contains("\"message\":\"Invalid internal API key\""), "body: " + json);
    }
}
