package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.outbound.TokenService;
import com.flashdrop.auth.application.usecase.ValidateTokenService;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Caso de uso de validacion de token (nombrado en el hallazgo I-2).
 *
 * <p>Es un delegador delgado a proposito: el caso de uso no interpreta ni
 * reescribe nada, y por eso lo que hay que fijar es justamente que no lo haga.
 * Si en algun momento alguien agrega logica aca —convertir la excepcion,
 * rellenar claims ausentes, aceptar tokens vencidos por unos segundos— estos
 * tests fallan, que es el objetivo.
 *
 * <p>La verificacion criptografica real (firma, expiracion, issuer) vive en
 * JwtTokenServiceTest, y el recorrido HTTP completo con un token real en
 * ApplicationStartupTest.
 */
class ValidateTokenServiceTest {

    private final TokenService tokens = mock(TokenService.class);
    private final ValidateTokenService service = new ValidateTokenService(tokens);

    @Test
    void delegaLaVerificacionYDevuelveLosClaimsSinAlterarlos() {
        var esperados = new TokenClaims(7L, "persona@flashdrop.cl", List.of("Cliente"));
        when(tokens.verify("un-token")).thenReturn(esperados);

        var obtenidos = service.validate("un-token");

        // Misma instancia: no se copia ni se transforma por el camino.
        assertSame(esperados, obtenidos);
        assertEquals(7L, obtenidos.userId());
        verify(tokens).verify("un-token");
        verifyNoMoreInteractions(tokens);
    }

    /**
     * El fallo de verificacion se propaga tal cual. Convertirlo en un
     * Optional vacio o en null dejaria pasar tokens invalidos como si fueran
     * ausencia de sesion.
     */
    @Test
    void propagaElFalloDeVerificacionSinConvertirlo() {
        when(tokens.verify("token-malo")).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> service.validate("token-malo"));
    }

    @Test
    void tampocoInterceptaTokensNulosOVacios() {
        when(tokens.verify(null)).thenThrow(new InvalidTokenException());
        when(tokens.verify("")).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> service.validate(null));
        assertThrows(InvalidTokenException.class, () -> service.validate(""));
    }
}
