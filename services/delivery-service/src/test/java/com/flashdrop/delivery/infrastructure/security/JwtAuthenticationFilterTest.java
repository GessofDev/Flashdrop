package com.flashdrop.delivery.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.CorrelationIdFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>Generates an RSA keypair at test time, signs tokens with nimbus, and
 * drives the filter directly via Mock servlet infrastructure. The
 * {@link JwksKeyProvider} is mocked so each test controls the lookup +
 * refresh behaviour precisely.
 */
@DisplayName("JwtAuthenticationFilterTest — PR-A: local JWKS RS256 verification")
class JwtAuthenticationFilterTest {

    private static final String ISSUER = "flashdrop-auth";
    private static final String KID = "test-kid";

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private JWK publicJwk;
    private JwksKeyProvider jwksKeyProvider;
    private JwtAuthenticationFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) kp.getPrivate();
        this.publicKey = (RSAPublicKey) kp.getPublic();
        this.publicJwk = new RSAKey.Builder(publicKey).keyID(KID).build().toPublicJWK();

        this.jwksKeyProvider = mock(JwksKeyProvider.class);
        this.filter = new JwtAuthenticationFilter(jwksKeyProvider, ISSUER);

        // Pre-seed MDC so ApiError bodies in 401s carry a traceId.
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-trace-id");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private String issueToken(long subject, String issuerOverride, long ttlSeconds) throws Exception {
        return issueToken(subject, issuerOverride, ttlSeconds, KID);
    }

    private String issueToken(long subject, String issuerOverride, long ttlSeconds, String kid) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(Long.toString(subject))
                .issuer(issuerOverride)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + ttlSeconds * 1000L))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }

    private MockHttpServletRequest buildRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/delivery/routes");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    private void assertApiError(MockHttpServletResponse response, String expectedMessageFragment) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.code()).isEqualTo("UNAUTHORIZED");
        assertThat(body.service()).isEqualTo("delivery-service");
        assertThat(body.traceId()).isEqualTo("test-trace-id");
        assertThat(body.message()).contains(expectedMessageFragment);
    }

    // ---------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("TC1: valid RS256 token → 200, principal = JWT subject (Long.toString)")
        void validToken_setsAuthenticationAndContinues() throws Exception {
            String token = issueToken(42L, ISSUER, 3600);
            when(jwksKeyProvider.findKeyByKid(KID)).thenReturn(Optional.of(publicJwk));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(buildRequest(token), response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).as("filter chain must proceed").isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("42");
            // No refresh on happy path.
            verify(jwksKeyProvider, never()).refresh();
        }
    }

    @Nested
    @DisplayName("Sad paths — 401 with ApiError")
    class SadPaths {

        @Test
        @DisplayName("TC2: missing Authorization header → 401")
        void missingHeader_returns401() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(buildRequest(null), response, chain);

            assertApiError(response, "Missing or malformed");
            assertThat(chain.getRequest()).as("filter chain must NOT proceed").isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(jwksKeyProvider, never()).findKeyByKid(anyString());
        }

        @Test
        @DisplayName("TC3: wrong issuer → 401")
        void wrongIssuer_returns401() throws Exception {
            String token = issueToken(42L, "evil-issuer", 3600);
            when(jwksKeyProvider.findKeyByKid(KID)).thenReturn(Optional.of(publicJwk));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(buildRequest(token), response, chain);

            assertApiError(response, "Invalid or expired");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("TC4: expired token → 401")
        void expiredToken_returns401() throws Exception {
            String token = issueToken(42L, ISSUER, -3600);
            when(jwksKeyProvider.findKeyByKid(KID)).thenReturn(Optional.of(publicJwk));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(buildRequest(token), response, chain);

            assertApiError(response, "Invalid or expired");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("TC5: unknown kid triggers refresh → after refresh key found → 200")
        void unknownKid_triggersRefresh_succeeds() throws Exception {
            String token = issueToken(42L, ISSUER, 3600);
            // First lookup fails (key not in cache); after refresh, lookup succeeds.
            when(jwksKeyProvider.findKeyByKid(KID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(publicJwk));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(buildRequest(token), response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("42");
            verify(jwksKeyProvider, atLeastOnce()).findKeyByKid(KID);
            verify(jwksKeyProvider, times(1)).refresh();
        }
    }
}