package com.flashdrop.delivery.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.infrastructure.security.JwtAuthenticationFilter;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security filter chain for delivery-service.
 *
 * <p>Matcher map:
 * <ul>
 *   <li>{@code /api/delivery/**}, {@code /delivery/**} — {@code authenticated()}.
 *       The actor's identity comes from the JWT subject (set by
 *       {@link JwtAuthenticationFilter}); the controller parses it as a
 *       {@code Long}.</li>
 *   <li>{@code /actuator/health/**}, {@code /actuator/info}, {@code /error} —
 *       {@code permitAll()} (health checks are anonymous by design).</li>
 *   <li>{@code /api/internal/**} — handled by {@code InternalApiKeyFilter}
 *       from {@code shared-observability} (X-Internal-Api-Key header).</li>
 *   <li>Everything else — also {@code authenticated()} as a defence-in-depth
 *       default; we never want an unauthenticated endpoint to leak through
 *       because of a missing matcher.</li>
 * </ul>
 *
 * <p>Stateless, CSRF disabled (mirrors orders-service's pattern).
 *
 * <p>The default Spring Security 6 {@link AuthenticationEntryPoint} returns
 * 403 — we override it with a 401 + {@link ApiError} entry point so that
 * unauthenticated callers see the same error envelope they would have seen
 * had the {@link JwtAuthenticationFilter} rejected them.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                // /api/internal/** is gated by InternalApiKeyFilter (registered
                // by shared-observability). Spring Security must NOT demand
                // JWT here — the X-Internal-Api-Key header is sufficient.
                .requestMatchers("/api/internal/**").permitAll()
                .requestMatchers("/api/delivery/**", "/delivery/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Spring Security 6 defaults to {@link Http403ForbiddenEntryPoint} when no
     * entry point is configured, but the rest of the system (including
     * {@link JwtAuthenticationFilter}) emits {@code 401} for missing/invalid
     * JWTs. We use {@code 401} here too so the contract is uniform.
     *
     * <p>Body shape matches {@link ApiError} so the dashboard / mobile clients
     * get a single, predictable envelope.
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = new ApiError(
                    "UNAUTHORIZED",
                    "delivery-service",
                    TraceContext.currentTraceId(),
                    "Authentication required");
            MAPPER.writeValue(response.getWriter(), body);
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = new ApiError(
                    "FORBIDDEN",
                    "delivery-service",
                    TraceContext.currentTraceId(),
                    "Access denied");
            MAPPER.writeValue(response.getWriter(), body);
        };
    }
}