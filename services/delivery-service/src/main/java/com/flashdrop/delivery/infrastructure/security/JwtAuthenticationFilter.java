package com.flashdrop.delivery.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates delivery-service requests with a {@code Bearer} JWT signed by
 * auth-service with RS256. Verification is LOCAL: the public key is resolved
 * from the JWKS endpoint advertised by auth-service, cached in
 * {@link JwksKeyProvider}, and refreshed on unknown {@code kid}.
 *
 * <p>On success, the filter sets a {@link UsernamePasswordAuthenticationToken}
 * whose principal name is the JWT subject (per auth-service's
 * {@code JwtTokenService}, the subject is {@code Long.toString(userId)},
 * parsed back via {@link Long#parseLong} at the controller).
 *
 * <p>On any failure (missing header, malformed token, bad signature, expired
 * token, wrong issuer, unknown kid after refresh), the filter writes a 401
 * with the standard {@link ApiError} envelope and DOES NOT invoke the rest of
 * the chain — the controller is never reached.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwksKeyProvider jwksKeyProvider;
    private final String expectedIssuer;

    public JwtAuthenticationFilter(JwksKeyProvider jwksKeyProvider,
                                   @Value("${auth.issuer}") String expectedIssuer) {
        this.jwksKeyProvider = jwksKeyProvider;
        this.expectedIssuer = expectedIssuer;
    }

    /**
     * Skip paths that are not gated by JWT auth. {@code /api/internal/*} is
     * handled by {@code InternalApiKeyFilter} from {@code shared-observability};
     * {@code /actuator/health/*} and {@code /actuator/info} are anonymous
     * health endpoints.
     *
     * <p>If we don't skip these here, the filter would 401 a perfectly valid
     * {@code X-Internal-Api-Key} request before Spring Security's
     * {@code permitAll()} matcher gets a chance.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/internal/")
                || uri.startsWith("/actuator/health")
                || uri.equals("/actuator/info")
                || uri.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        String kid = signedJWT.getHeader().getKeyID();
        if (kid == null || kid.isBlank()) {
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Look up key (refresh-on-unknown-kid: handles key rotation).
        Optional<JWK> jwk = jwksKeyProvider.findKeyByKid(kid);
        if (jwk.isEmpty()) {
            try {
                jwksKeyProvider.refresh();
            } catch (RuntimeException e) {
                log.warn("JWKS refresh failed during unknown-kid lookup: {}", e.getMessage());
            }
            jwk = jwksKeyProvider.findKeyByKid(kid);
        }
        if (jwk.isEmpty()) {
            log.warn("JWT validation failed: unknown kid={}", kid);
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Verify signature with the resolved RSA public key.
        try {
            JWSVerifier verifier = new RSASSAVerifier(jwk.get().toRSAKey().toRSAPublicKey());
            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature verification failed for kid={}", kid);
                writeUnauthorized(response, "Invalid or expired token");
                return;
            }
        } catch (JOSEException e) {
            log.warn("JWT verification error for kid={}: {}", kid, e.getMessage());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Inspect claims (issuer + expiration). Signature already verified.
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        if (expectedIssuer != null && !expectedIssuer.isBlank()
                && !expectedIssuer.equals(claims.getIssuer())) {
            log.warn("JWT issuer mismatch: expected={} actual={}", expectedIssuer, claims.getIssuer());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        Date exp = claims.getExpirationTime();
        if (exp != null && exp.before(new Date())) {
            log.warn("JWT expired at {}", exp);
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(subject, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("JWT validated: subject={}", subject);

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ApiError error = new ApiError(
                "UNAUTHORIZED",
                "delivery-service",
                TraceContext.currentTraceId(),
                message);
        MAPPER.writeValue(response.getWriter(), error);
    }
}