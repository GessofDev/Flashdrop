package cl.flashdrop.orders.config;

import cl.flashdrop.orders.infrastructure.exception.ErrorResponseWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

/**
 * Valida el JWT contra Auth y coloca el {@code userId} autenticado en el
 * {@link SecurityContextHolder} (GAP-04, auditoría 2026-09-04).
 *
 * <p>Auth (owner del JWT) expone la validación como una llamada de red
 * ({@code GET /auth/validate}), no como verificación local de firma — este filtro no
 * tiene ni necesita la clave pública de Auth. Una vez que Auth confirma que el token es
 * válido, es seguro leer localmente el claim {@code sub} del payload (ya autenticado por
 * Auth) para saber QUIÉN es el usuario: el {@code sub} es un {@code Long} convertido a
 * string (nunca un UUID — ver MIGRATION_PLAN.md / JwtTokenService de Auth), así que se
 * usa ese valor crudo como principal en vez del token completo, permitiendo que los
 * controllers hagan {@code Long.parseLong(authentication.getName())} igual que el resto
 * del ecosistema (Delivery hace lo mismo con su propio JWT subject).</p>
 */
@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidationFilter.class);

    // GAP-08/§11 (auditoría 2026-09-04): mismos valores que InternalServiceClientConfig
    // (5s connect / 10s read) — antes esta llamada a Auth no tenía timeout alguno y podía
    // colgar el hilo indefinidamente si Auth estaba lento (no caído).
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient authServiceClient;
    private final String authServiceUrl;

    // @Autowired explícito: sin esto, Spring ve dos constructores (éste y el de test) y
    // no puede elegir uno solo para inyección, aunque el otro sea package-private.
    @org.springframework.beans.factory.annotation.Autowired
    public JwtValidationFilter(@Value("${auth.service.url}") String authServiceUrl) {
        this(authServiceUrl, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /** Visible para tests: permite inyectar timeouts cortos para simular un Auth lento. */
    JwtValidationFilter(String authServiceUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.authServiceUrl = authServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.authServiceClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                ResponseEntity<Void> validation = authServiceClient.get()
                    .uri(authServiceUrl + "/auth/validate")
                    .header("Authorization", header)
                    .retrieve()
                    .toBodilessEntity();

                if (validation.getStatusCode().is2xxSuccessful()) {
                    String subject = extractSubject(token);
                    if (subject != null) {
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(subject, null, new ArrayList<>());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        logger.warn("Token validado por Auth pero sin claim 'sub' legible; no se autentica");
                    }
                }

            } catch (RestClientException e) {
                logger.warn("Auth service validation failed: {}", e.getMessage());
                ErrorResponseWriter.write(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Invalid token");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    /**
     * Decodifica localmente el payload del JWT (segundo segmento, base64url) para leer
     * {@code sub}. No verifica la firma — eso ya lo hizo Auth vía {@code /auth/validate}
     * justo antes de esta llamada; este método sólo extrae un claim de un token que ya
     * fue confirmado auténtico.
     */
    private static String extractSubject(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode sub = payload.get("sub");
            return sub != null ? sub.asText() : null;
        } catch (Exception e) {
            logger.warn("No se pudo leer el claim 'sub' del JWT: {}", e.getMessage());
            return null;
        }
    }
}
