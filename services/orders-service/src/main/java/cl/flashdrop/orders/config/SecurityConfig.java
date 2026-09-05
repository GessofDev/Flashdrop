package cl.flashdrop.orders.config;

import cl.flashdrop.orders.infrastructure.exception.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtValidationFilter jwtValidationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    public SecurityConfig(JwtValidationFilter jwtValidationFilter,
                          InternalApiKeyFilter internalApiKeyFilter) {
        this.jwtValidationFilter = jwtValidationFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // GAP-03 (auditoría 2026-09-04): POST /api/delivery/claim aceptaba
                // deliveryPersonId directo del body sin ninguna autenticación (IDOR).
                // Ahora exige el mismo JWT que /api/orders/** y DeliveryController
                // resuelve la identidad real desde el token (CurrentUserResolver),
                // ignorando el deliveryPersonId del body para efectos de autorización.
                .requestMatchers("/api/orders/**", "/api/delivery/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    ErrorResponseWriter.write(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"))
                .accessDeniedHandler((req, res, e) ->
                    ErrorResponseWriter.write(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Forbidden"))
            )
                .addFilterBefore(jwtValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter, JwtValidationFilter.class);

        return http.build();
    }
}
