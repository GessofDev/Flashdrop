package cl.flashdrop.orders.config;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GAP-08 (auditoría 2026-09-04): antes, la llamada de {@link JwtValidationFilter} a
 * {@code GET /auth/validate} no tenía timeout — un Auth lento (no caído) colgaba el hilo
 * indefinidamente en cualquier request a {@code /api/orders/**} o {@code /api/delivery/**}.
 * Usa el constructor "visible para tests" con timeouts cortos para no esperar los 10s
 * reales de producción en la suite.
 */
@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private JwtValidationFilter filter;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        String authServiceUrl = "http://localhost:" + wireMock.getPort();
        filter = new JwtValidationFilter(authServiceUrl, 200, 500);
        body = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    private static String jwtFor(long userId) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + userId + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    @Test
    void shouldAuthenticateWithSubjectFromTokenWhenAuthValidatesOk() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/auth/validate")).willReturn(aResponse().withStatus(200)));
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtFor(42L));

        filter.doFilterInternal(request, response, chain);

        assertEquals("42", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldReturn401WhenAuthRespondsSlowerThanTimeout() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/auth/validate"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000)));
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtFor(42L));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(body.toString().contains("\"error\":\"UNAUTHORIZED\""));
        verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSkipAuthenticationWhenNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
