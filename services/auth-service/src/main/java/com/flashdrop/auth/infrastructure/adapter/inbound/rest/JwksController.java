package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.infrastructure.adapter.outbound.security.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Publica la clave pública en formato JWKS para que el gateway (u otros
 * consumidores) validen los JWT sin conocer la clave privada.
 */
@RestController
public class JwksController {

    private final RsaKeyProvider keys;

    public JwksController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping("/auth/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = keys.publicKey();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keys.kid(),
                "n", b64.encodeToString(toUnsigned(pub.getModulus())),
                "e", b64.encodeToString(toUnsigned(pub.getPublicExponent())));
        return Map.of("keys", List.of(jwk));
    }

    private static byte[] toUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
