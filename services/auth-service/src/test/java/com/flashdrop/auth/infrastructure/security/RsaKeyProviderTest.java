package com.flashdrop.auth.infrastructure.security;

import com.flashdrop.auth.infrastructure.adapter.outbound.security.RsaKeyProvider;
import com.flashdrop.auth.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provision de claves RSA (hallazgo I-2 de la auditoria QA).
 *
 * <p>Lo que mas importa aca es el caso de produccion: si no se proveen claves y
 * la clave efimera esta deshabilitada, el servicio debe negarse a arrancar. Sin
 * esa comprobacion, un despliegue mal configurado levantaria firmando con una
 * clave nueva en cada reinicio, invalidando todas las sesiones y dejando al
 * gateway sin poder validar nada.
 */
class RsaKeyProviderTest {

    private static JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setIssuer("flashdrop-auth");
        p.setAccessExpirationMinutes(15);
        return p;
    }

    // --------------------------------------------------------- clave efimera

    @Test
    void conClaveEfimeraPermitidaGeneraUnParUtilizable() {
        JwtProperties p = props();
        p.setAllowEphemeralKey(true);

        var claves = new RsaKeyProvider(p);

        assertNotNull(claves.privateKey());
        assertNotNull(claves.publicKey());
        assertEquals("RSA", claves.publicKey().getAlgorithm());
        assertTrue(claves.publicKey().getModulus().bitLength() >= 2048,
                "La clave debe ser de al menos 2048 bits");
    }

    @Test
    void cadaArranqueEfimeroGeneraUnParDistinto() {
        JwtProperties p = props();
        p.setAllowEphemeralKey(true);

        assertNotEquals(new RsaKeyProvider(p).kid(), new RsaKeyProvider(p).kid());
    }

    // ------------------------------------------------------- caso critico

    @Test
    void sinClavesYSinPermisoDeEfimeraElArranqueFalla() {
        JwtProperties p = props();
        p.setAllowEphemeralKey(false);   // el valor por defecto en application.yml

        var error = assertThrows(IllegalStateException.class, () -> new RsaKeyProvider(p));

        assertTrue(error.getMessage().contains("jwt.private-key"),
                "El mensaje debe decir que falta configurar: " + error.getMessage());
    }

    // ----------------------------------------------------- claves provistas

    @Test
    void cargaUnParProvistoEnFormatoPem() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair par = g.generateKeyPair();

        JwtProperties p = props();
        p.setAllowEphemeralKey(false);   // con claves provistas no hace falta
        p.setPrivateKey(pem("PRIVATE KEY", par.getPrivate().getEncoded()));
        p.setPublicKey(pem("PUBLIC KEY", par.getPublic().getEncoded()));

        var claves = new RsaKeyProvider(p);

        assertEquals(par.getPublic(), claves.publicKey());
        assertEquals(par.getPrivate(), claves.privateKey());
    }

    /**
     * El kid identifica la clave dentro del JWKS. Debe derivarse de la clave
     * publica: si cambiara entre arranques con la misma clave, el gateway
     * cachearia una entrada que nunca vuelve a coincidir.
     */
    @Test
    void elKidEsEstableParaLaMismaClave() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair par = g.generateKeyPair();

        JwtProperties p = props();
        p.setPrivateKey(pem("PRIVATE KEY", par.getPrivate().getEncoded()));
        p.setPublicKey(pem("PUBLIC KEY", par.getPublic().getEncoded()));

        assertEquals(new RsaKeyProvider(p).kid(), new RsaKeyProvider(p).kid());
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + tipo + "-----";
    }
}
