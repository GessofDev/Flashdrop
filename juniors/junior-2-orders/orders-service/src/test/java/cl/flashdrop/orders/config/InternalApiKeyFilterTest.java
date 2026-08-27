package cl.flashdrop.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter("dev-key");
    }

    @Test
    void shouldAllowWhenValidKey() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("dev-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void shouldRejectWhenKeyMissing() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenKeyIncorrect() throws Exception {
        when(request.getServletPath()).thenReturn("/api/internal/orders");
        when(request.getHeader(InternalApiKeyFilter.API_KEY_HEADER)).thenReturn("wrong");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotAffectPublicEndpoints() throws Exception {
        when(request.getServletPath()).thenReturn("/api/orders");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
}
