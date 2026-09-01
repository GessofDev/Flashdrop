package com.flashdrop.catalog.infrastructure.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${internal.api.key:${services.internal-api-key:dev-key}}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (requiresInternalApiKey(request)) {
            String apiKey = request.getHeader("X-Internal-Api-Key");

            if (!expectedApiKey.equals(apiKey)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"Invalid internal API key\"}"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresInternalApiKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        return uri.startsWith("/api/internal")
                || ("POST".equalsIgnoreCase(method) && "/catalog/products".equals(uri))
                || ("POST".equalsIgnoreCase(method) && "/catalog/products/validate".equals(uri));
    }
}
