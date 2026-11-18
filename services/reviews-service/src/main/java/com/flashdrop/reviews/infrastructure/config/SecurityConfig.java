package com.flashdrop.reviews.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import com.flashdrop.reviews.infrastructure.security.JwtAuthenticationFilter;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security filter chain for reviews-service.
 *
 * <p>Matcher map:
 * <ul>
 *   <li>{@code /actuator/health/**}, {@code /actuator/info}, {@code /error} —
 *       {@code permitAll()} (health checks are anonymous by design).</li>
 *   <li>{@code /api/internal/**} — handled by {@code InternalApiKeyFilter} from
 *       {@code shared-observability} (X-Internal-Api-Key header). Spring
 *       Security must NOT demand a JWT here.</li>
 *   <li>{@code /api/reviews/**}, {@code /api/users/**} — any of the three
 *       roles of autor (Cliente | Repartidor | Restaurante). La decisión de
 *       <b>qué</b> relación está permitida la toma la política de dominio
 *       ({@code RatingPolicy}) + la participación en la orden, no el matcher.</li>
 *   <li>Everything else — also authenticated with one of the three roles as a
 *       defence-in-depth default.</li>
 * </ul>
 *
 * <p>Stateless, CSRF disabled (mirrors orders-service's pattern).</p>
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
                // by shared-observability). The X-Internal-Api-Key header is sufficient.
                .requestMatchers("/api/internal/**").permitAll()
                .requestMatchers("/api/reviews/**", "/api/users/**")
                    .hasAnyRole("Cliente", "Repartidor", "Restaurante")
                .anyRequest().hasAnyRole("Cliente", "Repartidor", "Restaurante")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Spring Security 6 defaults to 403 when no entry point is configured, but
     * the rest of the system emits 401 for missing/invalid JWTs. We use 401
     * here too so the contract is uniform.
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
                    "reviews-service",
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
                    "reviews-service",
                    TraceContext.currentTraceId(),
                    "Access denied");
            MAPPER.writeValue(response.getWriter(), body);
        };
    }
}
