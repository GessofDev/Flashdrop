package com.flashdrop.observability.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates internal service-to-service calls via the {@code X-Internal-Api-Key}
 * header. Applies only to paths matching {@code /api/internal/*}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code /api/internal/*} with valid key  &rarr; chain continues (200)</li>
 *   <li>{@code /api/internal/*} with missing key &rarr; 401 + {@link ApiError}</li>
 *   <li>{@code /api/internal/*} with wrong key   &rarr; 401 + {@link ApiError}</li>
 *   <li>Any other path                             &rarr; filter skipped (passthrough)</li>
 * </ul>
 *
 * <p>Constant-time comparison via {@link ApiKeyValidator#isValid} prevents timing attacks.
 * The service name in the error response defaults to {@code "shared-observability"}
 * and can be overridden via constructor.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String expectedKey;
    private final String serviceName;

    /**
     * @param expectedKey the configured internal API key; must not be null
     */
    public InternalApiKeyFilter(String expectedKey) {
        this(expectedKey, "shared-observability");
    }

    /**
     * @param expectedKey  the configured internal API key; must not be null
     * @param serviceName  the service name to embed in error responses
     */
    public InternalApiKeyFilter(String expectedKey, String serviceName) {
        this.expectedKey = expectedKey;
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER);

        if (!ApiKeyValidator.isValid(providedKey, expectedKey)) {
            writeUnauthorizedResponse(response, request.getRequestURI());
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ApiError error = new ApiError(
                "UNAUTHORIZED",
                serviceName,
                TraceContext.currentTraceId(),
                "Missing or invalid internal API key for path: " + path
        );
        MAPPER.writeValue(response.getWriter(), error);
    }
}
