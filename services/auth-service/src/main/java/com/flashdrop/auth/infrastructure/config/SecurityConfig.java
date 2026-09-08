package com.flashdrop.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless y default-deny (S-01). auth-service solo expone endpoints públicos
 * de identidad; /profile valida el token internamente en el controlador.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())   // toma el bean corsConfigurationSource de CorsConfig
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login",
                        "/auth/refresh", "/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/validate", "/auth/profile",
                        "/auth/.well-known/jwks.json").permitAll()
                // La autenticacion real de estas rutas la realiza InternalApiKeyFilter.
                .requestMatchers("/api/internal/**").permitAll()
                // El gateway hace polling de /health en cada servicio para la
                // agregación de estado; sin este permitAll cae en denyAll (403).
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Permitir pre-flight CORS
                .anyRequest().denyAll());
        return http.build();
    }

}
