package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.RefreshCommand;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.outbound.TokenService;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.RefreshTokenManager;
import com.flashdrop.auth.application.usecase.RefreshTokenService;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUTH-UNIT-06 — refresh token valido produce un nuevo access JWT.
 *
 * <p>Unit test puro: se instancia {@link RefreshTokenService} de verdad y se
 * aislan sus tres dependencias. Sin Spring, sin HTTP, sin base de datos.
 *
 * <p>Cubre el tramo que ningun otro test alcanzaba. RefreshTokenManagerTest
 * verifica la rotacion del token opaco, pero el manager no conoce
 * {@link TokenService}, asi que le es estructuralmente imposible demostrar la
 * emision del JWT. Y el test de AuthController simula el caso de uso completo,
 * de modo que verifica la traduccion HTTP pero no ejecuta esta logica. Ambos
 * cubren responsabilidades distintas y se conservan como estan.
 */
class RefreshTokenServiceTest {

    private static final Long ID_USUARIO = 7L;
    private static final String REFRESH_CRUDO = "refresh-opaco-valido";
    private static final String REFRESH_NUEVO = "nuevo-refresh-opaco";
    private static final String JWT_NUEVO = "nuevo-jwt";
    private static final long TTL = 900L;

    private final RefreshTokenManager refreshTokens = mock(RefreshTokenManager.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenService tokens = mock(TokenService.class);

    private final RefreshTokenService service =
            new RefreshTokenService(refreshTokens, users, tokens);

    private static User usuario() {
        return new User(ID_USUARIO, new Email("persona@flashdrop.cl"), null, "Persona", "Demo",
                "+56911112222", null,
                List.of(new Role(1L, "Cliente", "/client/products/list")),
                Instant.now());
    }

    /**
     * El caso obligatorio: un refresh token valido termina produciendo un
     * access JWT nuevo, y ese JWT es el que llega al resultado.
     */
    @Test
    void unRefreshTokenValidoProduceUnNuevoAccessJwt() {
        when(refreshTokens.rotate(REFRESH_CRUDO))
                .thenReturn(new RefreshTokenManager.Rotation(ID_USUARIO, REFRESH_NUEVO));
        when(users.findById(ID_USUARIO)).thenReturn(Optional.of(usuario()));
        when(tokens.issue(any(TokenClaims.class))).thenReturn(JWT_NUEVO);
        when(tokens.accessTtlSeconds()).thenReturn(TTL);

        var par = service.refresh(new RefreshCommand(REFRESH_CRUDO));

        // 1. El JWT emitido es el que termina en el resultado.
        assertEquals(JWT_NUEVO, par.accessToken());
        // 2. El refresh token que devuelve la rotacion se conserva, y no se
        //    confunde con el JWT: son valores de naturaleza distinta.
        assertEquals(REFRESH_NUEVO, par.refreshToken());
        assertEquals(TTL, par.expiresInSeconds());
    }

    /**
     * El JWT se emite para el usuario que devolvio la rotacion, con su correo
     * y sus roles. Sin esta comprobacion, el servicio podria emitir un token
     * valido para la persona equivocada y el test anterior no lo notaria.
     */
    @Test
    void elJwtSeEmiteConLosClaimsDelUsuarioDeLaRotacion() {
        when(refreshTokens.rotate(REFRESH_CRUDO))
                .thenReturn(new RefreshTokenManager.Rotation(ID_USUARIO, REFRESH_NUEVO));
        when(users.findById(ID_USUARIO)).thenReturn(Optional.of(usuario()));
        when(tokens.issue(any(TokenClaims.class))).thenReturn(JWT_NUEVO);

        service.refresh(new RefreshCommand(REFRESH_CRUDO));

        var claims = ArgumentCaptor.forClass(TokenClaims.class);
        verify(tokens).issue(claims.capture());
        assertEquals(ID_USUARIO, claims.getValue().userId());
        assertEquals("persona@flashdrop.cl", claims.getValue().email());
        assertEquals(List.of("Cliente"), claims.getValue().roles());
    }

    /**
     * Cobertura adicional: la rotacion puede devolver el id de un usuario que
     * ya no existe. En ese caso no debe emitirse ningun token.
     */
    @Test
    void siElUsuarioYaNoExisteNoSeEmiteNingunToken() {
        when(refreshTokens.rotate(REFRESH_CRUDO))
                .thenReturn(new RefreshTokenManager.Rotation(ID_USUARIO, REFRESH_NUEVO));
        when(users.findById(ID_USUARIO)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> service.refresh(new RefreshCommand(REFRESH_CRUDO)));

        verify(tokens, never()).issue(any());
    }

    /**
     * Si la rotacion rechaza el token —vencido, revocado o inexistente— el
     * fallo se propaga y no se consulta al usuario ni se emite nada.
     */
    @Test
    void siLaRotacionFallaNoSeConsultaAlUsuarioNiSeEmiteToken() {
        when(refreshTokens.rotate("refresh-invalido")).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class,
                () -> service.refresh(new RefreshCommand("refresh-invalido")));

        verify(users, never()).findById(any());
        verify(tokens, never()).issue(any());
    }

    /** El logout delega la revocacion, sin tocar la emision de tokens. */
    @Test
    void elLogoutRevocaElTokenYNoEmiteNadaNuevo() {
        service.logout(REFRESH_CRUDO);

        verify(refreshTokens).revoke(REFRESH_CRUDO);
        verify(tokens, never()).issue(any());
    }
}
