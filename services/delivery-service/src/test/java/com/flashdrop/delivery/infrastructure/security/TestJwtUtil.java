package com.flashdrop.delivery.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

/**
 * Generates real signed JWTs for integration testing of JwtAuthenticationFilter.
 * Keys are generated once per test class to avoid regeneration overhead.
 */
public class TestJwtUtil {

    private static RSAKey rsaKey;
    private static String kid = "test-kid-1";

    public static synchronized RSAKey getOrCreateRsaKey() throws Exception {
        if (rsaKey == null) {
            KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
            kg.initialize(2048);
            KeyPair kp = kg.generateKeyPair();
            rsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                    .privateKey((RSAPrivateKey) kp.getPrivate())
                    .keyID(kid)
                    .build();
        }
        return rsaKey;
    }

    public static String generateValidJwt(String subject) throws Exception {
        RSAKey key = getOrCreateRsaKey();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("auth-service")
                .expirationTime(new Date(Instant.now().plusSeconds(3600).toEpochMilli()))
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        RSASSASigner signer = new RSASSASigner(key);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    public static RSAKey getPublicKey() throws Exception {
        return getOrCreateRsaKey().toPublicJWK();
    }

    public static String getKid() {
        return kid;
    }
}
