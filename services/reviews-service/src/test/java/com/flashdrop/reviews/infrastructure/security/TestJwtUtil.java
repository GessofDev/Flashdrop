package com.flashdrop.reviews.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Genera JWTs reales (firmados con RS256) para los tests de la API.
 *
 * <p>Espejo de {@code delivery-service/.../infrastructure/security/TestJwtUtil}:
 * los tests de reviews-service ejercitan el filtro de autenticación real con
 * tokens firmados, no con authorities inyectadas a mano.</p>
 */
public final class TestJwtUtil {

    private static final String KID = "reviews-test-kid";
    private static final String ISSUER = "flashdrop-auth";

    private static RSAKey rsaKey;

    private TestJwtUtil() {
        // utility class
    }

    public static synchronized RSAKey getOrCreateRsaKey() throws Exception {
        if (rsaKey == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KID)
                    .build();
        }
        return rsaKey;
    }

    /**
     * @param subject the {@code sub} claim (Long.toString(userId)).
     * @param roles   values for the {@code roles} claim, or {@code null} to omit it.
     */
    public static String generateValidJwt(String subject, List<String> roles) throws Exception {
        RSAKey key = getOrCreateRsaKey();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .build();

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .expirationTime(new Date(Instant.now().plusSeconds(3600).toEpochMilli()));
        if (roles != null) {
            claims.claim("roles", roles);
        }

        SignedJWT signedJWT = new SignedJWT(header, claims.build());
        signedJWT.sign(new RSASSASigner(key));
        return signedJWT.serialize();
    }

    public static String generateValidJwt(String subject) throws Exception {
        return generateValidJwt(subject, null);
    }

    public static RSAKey getPublicKey() throws Exception {
        return getOrCreateRsaKey().toPublicJWK();
    }

    public static String getKid() {
        return KID;
    }
}
