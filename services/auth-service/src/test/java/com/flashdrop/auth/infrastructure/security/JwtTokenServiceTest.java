package com.flashdrop.auth.infrastructure.security;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.infrastructure.adapter.outbound.security.JwtTokenService;
import com.flashdrop.auth.infrastructure.adapter.outbound.security.RsaKeyProvider;
import com.flashdrop.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Emision y verificacion de JWT (hallazgo I-2 de la auditoria QA).
 *
 * <p>La firma es la unica garantia de que un token no fue fabricado por un
 * tercero, y hasta esta auditoria no habia ninguna prueba sobre ella. Cada caso
 * de abajo corresponde a una forma distinta de intentar colar un token.
 */
class JwtTokenServiceTest {

    private static JwtProperties props(String issuer, int minutos) {
        JwtProperties p = new JwtProperties();
        p.setIssuer(issuer);
        p.setAccessExpirationMinutes(minutos);
        p.setAllowEphemeralKey(true);   // par efimero, suficiente para la prueba
        return p;
    }

    private static JwtTokenService servicio(JwtProperties p) {
        return new JwtTokenService(new RsaKeyProvider(p), p);
    }

    private static final TokenClaims CLAIMS =
            new TokenClaims(7L, "persona@flashdrop.cl", List.of("Cliente", "Restaurante"));

    // ------------------------------------------------------------- emision

    @Test
    void emiteUnTokenQueSePuedeVerificarYConservaLosClaims() {
        var jwt = servicio(props("flashdrop-auth", 15));

        var verificado = jwt.verify(jwt.issue(CLAIMS));

        assertEquals(7L, verificado.userId());
        assertEquals("persona@flashdrop.cl", verificado.email());
        assertEquals(List.of("Cliente", "Restaurante"), verificado.roles());
    }

    @Test
    void elTokenLlevaElKidDeLaClaveQueLoFirmo() {
        var p = props("flashdrop-auth", 15);
        var claves = new RsaKeyProvider(p);
        var jwt = new JwtTokenService(claves, p);

        String cabecera = new String(Base64.getUrlDecoder()
                .decode(jwt.issue(CLAIMS).split("\\.")[0]));

        assertTrue(cabecera.contains(claves.kid()),
                "El gateway necesita el kid para elegir la clave del JWKS: " + cabecera);
    }

    @Test
    void elTtlDeclaradoCoincideConLaConfiguracion() {
        assertEquals(15 * 60, servicio(props("flashdrop-auth", 15)).accessTtlSeconds());
    }

    // ------------------------------------------------------------- rechazos

    @Test
    void rechazaUnTokenExpirado() {
        var p = props("flashdrop-auth", 15);
        var claves = new RsaKeyProvider(p);
        var jwt = new JwtTokenService(claves, p);

        // Se firma con la clave buena, pero con expiracion en el pasado.
        String vencido = Jwts.builder()
                .header().keyId(claves.kid()).and()
                .issuer("flashdrop-auth")
                .subject("7")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(claves.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThrows(InvalidTokenException.class, () -> jwt.verify(vencido));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        var p = props("flashdrop-auth", 15);
        var jwtLegitimo = servicio(p);
        // Otro par de claves: simula a un tercero que fabrica su propio token.
        var jwtImpostor = servicio(props("flashdrop-auth", 15));

        assertThrows(InvalidTokenException.class,
                () -> jwtLegitimo.verify(jwtImpostor.issue(CLAIMS)));
    }

    @Test
    void rechazaUnTokenConLaFirmaAlterada() {
        var jwt = servicio(props("flashdrop-auth", 15));
        String[] partes = jwt.issue(CLAIMS).split("\\.");

        // Se cambia un caracter de la firma manteniendo el resto intacto.
        char primero = partes[2].charAt(0);
        String firmaRota = (primero == 'A' ? 'B' : 'A') + partes[2].substring(1);
        String manipulado = partes[0] + "." + partes[1] + "." + firmaRota;

        assertThrows(InvalidTokenException.class, () -> jwt.verify(manipulado));
    }

    @Test
    void rechazaUnTokenConIssuerDistinto() {
        var otroEmisor = servicio(props("otro-emisor", 15));
        var nuestro = servicio(props("flashdrop-auth", 15));

        // Aunque la firma fuera valida, el issuer no es el nuestro.
        assertThrows(InvalidTokenException.class,
                () -> nuestro.verify(otroEmisor.issue(CLAIMS)));
    }

    @Test
    void rechazaBasuraYCadenasVacias() {
        var jwt = servicio(props("flashdrop-auth", 15));

        assertThrows(InvalidTokenException.class, () -> jwt.verify("no-es-un-jwt"));
        assertThrows(InvalidTokenException.class, () -> jwt.verify(""));
        assertThrows(InvalidTokenException.class, () -> jwt.verify("a.b.c"));
    }

    /**
     * Al rotar la clave, los tokens emitidos con la anterior dejan de valer.
     * Es el comportamiento buscado, pero conviene tenerlo escrito: implica que
     * una rotacion invalida las sesiones activas.
     */
    @Test
    void rotarLaClaveInvalidaLosTokensAnteriores() {
        var p = props("flashdrop-auth", 15);
        var antes = new RsaKeyProvider(p);
        var jwtAntes = new JwtTokenService(antes, p);
        String tokenViejo = jwtAntes.issue(CLAIMS);

        var despues = new RsaKeyProvider(p);
        var jwtDespues = new JwtTokenService(despues, p);

        assertNotNull(jwtDespues.issue(CLAIMS));
        assertThrows(InvalidTokenException.class, () -> jwtDespues.verify(tokenViejo));
    }
}
