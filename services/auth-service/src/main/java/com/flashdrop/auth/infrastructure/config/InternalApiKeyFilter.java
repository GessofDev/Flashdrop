package com.flashdrop.auth.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PREFIX = "/api/internal/";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(
            @Value("${services.internal-api-key:}") String expectedKey,
            ObjectMapper objectMapper) {
        this.expectedKey = expectedKey == null
                ? new byte[0]
                : expectedKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        byte[] suppliedKey = supplied == null
                ? new byte[0]
                : supplied.getBytes(StandardCharsets.UTF_8);

        if (expectedKey.length == 0 || !MessageDigest.isEqual(expectedKey, suppliedKey)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                    response.getWriter(),
                    new ErrorResponse(403, "FORBIDDEN", "Invalid internal API key"));
            return;
        }

        chain.doFilter(request, response);
    }
}
