package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.AuthResult;
import com.flashdrop.auth.application.dto.RegisterUserResult;
import com.flashdrop.auth.application.port.inbound.AuthenticateUserUseCase;
import com.flashdrop.auth.application.port.inbound.GetUserProfileUseCase;
import com.flashdrop.auth.application.port.inbound.LogoutUseCase;
import com.flashdrop.auth.application.port.inbound.RefreshTokenUseCase;
import com.flashdrop.auth.application.port.inbound.RegisterUserUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.EmailAlreadyRegisteredException;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP de /auth/**: códigos de estado, forma del cuerpo y traducción
 * de excepciones de dominio.
 *
 * <p>Reemplaza al antiguo {@code AuthIntegrationTest}, que levantaba
 * Testcontainers + Flyway + JPA — un stack que este servicio no tiene
 * (la persistencia es PostgREST) y que por eso nunca llegaba a ejecutarse.
 * Aquí los casos de uso están mockeados: se verifica la capa REST, que es
 * lo que el test anterior decía cubrir y no cubría.
 */
class AuthControllerTest {

    private final RegisterUserUseCase registerUser = mock(RegisterUserUseCase.class);
    private final AuthenticateUserUseCase authenticateUser = mock(AuthenticateUserUseCase.class);
    private final ValidateTokenUseCase validateToken = mock(ValidateTokenUseCase.class);
    private final RefreshTokenUseCase refreshToken = mock(RefreshTokenUseCase.class);
    private final LogoutUseCase logout = mock(LogoutUseCase.class);
    private final GetUserProfileUseCase getUserProfile = mock(GetUserProfileUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new AuthController(registerUser, authenticateUser, validateToken,
                refreshToken, logout, getUserProfile);
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registroDevuelve201ConElIdCreado() throws Exception {
        when(registerUser.register(any())).thenReturn(new RegisterUserResult(42L));

        mvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@flashdrop.cl","password":"Segura1234",
                                 "name":"Nuevo","lastName":"Cliente","phone":"+56911112222"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42));
    }

    @Test
    void loginDevuelve200ConAccessYRefreshToken() throws Exception {
        when(authenticateUser.authenticate(any())).thenReturn(new AuthResult(
                1L, "Cliente", "cliente@demo.cl", List.of("Cliente"),
                "access-jwt", "refresh-opaco", 900L));

        mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"cliente@demo.cl","password":"Segura1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaco"))
                .andExpect(jsonPath("$.roles[0]").value("Cliente"));
    }

    @Test
    void credencialesInvalidasDevuelven401ConCodigoTipado() throws Exception {
        when(authenticateUser.authenticate(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"noexiste@flashdrop.cl","password":"loquesea123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.service").value("auth-service"));
    }

    @Test
    void emailYaRegistradoDevuelve409() throws Exception {
        when(registerUser.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@demo.cl","password":"Segura1234"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void bodySinPasswordDevuelve400() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@flashdrop.cl"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void profileSinHeaderAuthorizationDevuelve401() throws Exception {
        mvc.perform(get("/auth/profile"))
                .andExpect(status().isUnauthorized());
    }
}
