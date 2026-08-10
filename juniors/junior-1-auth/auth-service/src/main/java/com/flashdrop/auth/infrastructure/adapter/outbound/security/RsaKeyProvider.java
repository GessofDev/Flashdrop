package com.flashdrop.auth.infrastructure.adapter.outbound.security;

import com.flashdrop.auth.infrastructure.config.JwtProperties;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Provee el par de claves RSA para firmar (RS256) y para publicar el JWKS.
 * Producción: claves provistas por entorno. Dev: efímeras si se permite.
 */
@Component
public class RsaKeyProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String kid;

    public RsaKeyProvider(JwtProperties props) {
        try {
            if (StringUtils.hasText(props.getPrivateKey())) {
                this.privateKey = loadPrivate(props.getPrivateKey());
                this.publicKey = loadPublic(props.getPublicKey());
            } else {
                if (!props.isAllowEphemeralKey()) {
                    throw new IllegalStateException(
                            "Falta jwt.private-key. Define las claves RSA por entorno o activa " +
                            "JWT_ALLOW_EPHEMERAL=true solo en entornos no productivos (S-02).");
                }
                KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                g.initialize(2048);
                KeyPair kp = g.generateKeyPair();
                this.privateKey = (RSAPrivateKey) kp.getPrivate();
                this.publicKey = (RSAPublicKey) kp.getPublic();
                LoggerFactory.getLogger(getClass())
                        .warn("Clave RSA EFÍMERA generada al arranque — NO apto para producción.");
            }
            this.kid = computeKid(publicKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudieron preparar las claves JWT", e);
        }
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String kid() { return kid; }

    private static RSAPrivateKey loadPrivate(String pem) throws GeneralSecurityException {
        byte[] der = decodePem(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey loadPublic(String pem) throws GeneralSecurityException {
        byte[] der = decodePem(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] decodePem(String pem) {
        String body = pem.replaceAll("-----BEGIN[^-]*-----", "")
                          .replaceAll("-----END[^-]*-----", "")
                          .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String computeKid(RSAPublicKey key) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
    }
}
