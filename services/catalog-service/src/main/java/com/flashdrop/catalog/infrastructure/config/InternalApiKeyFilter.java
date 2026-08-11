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

    public InternalApiKeyFilter(@Value("${services.internal-api-key:dev-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/internal")) {
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
}
