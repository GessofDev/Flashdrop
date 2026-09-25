package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.AuthResult;
import com.flashdrop.auth.application.dto.RegisterUserResult;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.AuthenticateUserUseCase;
import com.flashdrop.auth.application.port.inbound.GetUserProfileUseCase;
import com.flashdrop.auth.application.port.inbound.LogoutUseCase;
import com.flashdrop.auth.application.port.inbound.RefreshTokenUseCase;
import com.flashdrop.auth.application.port.inbound.RegisterUserUseCase;
import com.flashdrop.auth.application.port.inbound.UpdateUserProfileUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.EmailAlreadyRegisteredException;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP de /auth/**: códigos de estado, forma del cuerpo y traducción
 * de excepciones de dominio.
 *
 * <p>Los casos de uso están simulados a propósito: acá se verifica solo la
 * capa REST — códigos, forma del cuerpo y traducción de excepciones — sin
 * involucrar persistencia.
 *
 * <p>Se monta con {@code standaloneSetup}, que <b>no</b> aplica la cadena de
 * filtros de Spring Security: los códigos de acá reflejan lo que devuelven el
 * controlador y el {@code @RestControllerAdvice}, no lo que vería un cliente
 * real atravesando la seguridad. Esa parte se verifica en
 * {@code ApplicationStartupTest}, sobre el contexto completo. El comportamiento contra una base real lo cubre
 * {@code AuthPostgresIntegrationTest}, y el cableado de la aplicación
 * {@code ApplicationStartupTest}.
 */
class AuthControllerTest {

    private final RegisterUserUseCase registerUser = mock(RegisterUserUseCase.class);
    private final AuthenticateUserUseCase authenticateUser = mock(AuthenticateUserUseCase.class);
    private final ValidateTokenUseCase validateToken = mock(ValidateTokenUseCase.class);
    private final RefreshTokenUseCase refreshToken = mock(RefreshTokenUseCase.class);
    private final LogoutUseCase logout = mock(LogoutUseCase.class);
    private final GetUserProfileUseCase getUserProfile = mock(GetUserProfileUseCase.class);
    private final UpdateUserProfileUseCase updateUserProfile = mock(UpdateUserProfileUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new AuthController(registerUser, authenticateUser, validateToken,
                refreshToken, logout, getUserProfile, updateUserProfile);
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

    /** Gap QA: contrato HTTP del refresh cuando el token ya vencio. El caso
     *  unitario existia en RefreshTokenManagerTest, pero no la respuesta HTTP. */
    @Test
    void refreshConTokenVencidoDevuelve401ConCodigoTipado() throws Exception {
        when(refreshToken.refresh(any())).thenThrow(new InvalidTokenException());

        mvc.perform(post("/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"token-ya-vencido"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    /** Gap QA: el logout es idempotente. Un token inexistente no es un error:
     *  el objetivo del llamador —que esa sesion no sirva— ya se cumple. */
    @Test
    void logoutConTokenInexistenteDevuelve204() throws Exception {
        mvc.perform(post("/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"token-que-nunca-existio"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutSinCuerpoDevuelve400() throws Exception {
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** Gap QA: telefono duplicado. `users.phone` es unico y el alta no lo
     *  comprueba antes de insertar, asi que la violacion llegaba como 500.
     *  Ahora se traduce al 409 que la seccion 10 del plan reserva para
     *  "recurso ya existe", con mensaje generico para no permitir enumerar
     *  telefonos registrados. */
    @Test
    void telefonoDuplicadoDevuelve409YNoRevelaElCampo() throws Exception {
        when(registerUser.register(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"users_phone_key\""));

        mvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"otro@flashdrop.cl","password":"Segura1234",
                                 "phone":"+56911111111"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Ya existe un registro con esos datos"));
    }

    /**
     * I-3: en rutas publicas el 404 sale como ApiError, con code y traceId. El
     * formato {status, error, message} queda reservado para /api/internal/**,
     * que es donde la seccion 10 del plan lo exige.
     */
    @Test
    void usuarioInexistenteEnRutaPublicaUsaElFormatoDeObservabilidad() throws Exception {
        when(validateToken.validate(any()))
                .thenReturn(new com.flashdrop.auth.application.dto.TokenClaims(
                        99L, "fantasma@flashdrop.cl", java.util.List.of()));
        when(getUserProfile.getProfile(99L))
                .thenThrow(new UserNotFoundException("User not found with id: 99"));

        mvc.perform(get("/auth/profile").header("Authorization", "Bearer un-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void profileSinHeaderAuthorizationDevuelve401() throws Exception {
        mvc.perform(get("/auth/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------ PUT /auth/profile

    private static final String PERFIL_VALIDO = """
            {"name":"Nico","lastName":"Leiva","phone":"+56999998888",
             "photo":"https://img.flashdrop.cl/perfil.png"}
            """;

    private void tokenDelUsuario(long userId) {
        when(validateToken.validate(any()))
                .thenReturn(new TokenClaims(userId, "cliente@demo.cl", List.of("Cliente")));
    }

    /** Mismo UserProfile que el GET, sin envoltorio: el userId va en la raiz. */
    @Test
    void editarPerfilDevuelve200ConElPerfilActualizadoSinEnvoltorio() throws Exception {
        tokenDelUsuario(1L);
        when(updateUserProfile.updateProfile(eq(1L), any())).thenReturn(new UserProfile(
                1L, "Nico", "Leiva", "cliente@demo.cl", "+56999998888", null,
                "https://img.flashdrop.cl/perfil.png", List.of("Cliente")));

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON).content(PERFIL_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Nico"))
                .andExpect(jsonPath("$.phone").value("+56999998888"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /** El usuario sale del token. Un userId o un email en el cuerpo no
     *  cambian a quien se edita ni hacen fallar la peticion. */
    @Test
    void editarPerfilIgnoraUserIdYEmailDelCuerpo() throws Exception {
        tokenDelUsuario(1L);

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":999,"email":"otro@flashdrop.cl",
                                 "name":"Nico","lastName":"Leiva"}
                                """))
                .andExpect(status().isOk());

        verify(updateUserProfile).updateProfile(eq(1L), any());
    }

    @Test
    void editarPerfilSinTokenDevuelve401() throws Exception {
        mvc.perform(put("/auth/profile").contentType(APPLICATION_JSON).content(PERFIL_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void editarPerfilSinNombreDevuelve400() throws Exception {
        tokenDelUsuario(1L);

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"","lastName":"Leiva"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void editarPerfilConTelefonoMalFormadoDevuelve400() throws Exception {
        tokenDelUsuario(1L);
        when(updateUserProfile.updateProfile(eq(1L), any()))
                .thenThrow(new InvalidUserException("Teléfono inválido"));

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Nico","lastName":"Leiva","phone":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** Un cuerpo que no es JSON es un error del llamador: 400, no 500. */
    @Test
    void editarPerfilConCuerpoQueNoEsJsonDevuelve400() throws Exception {
        tokenDelUsuario(1L);

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON).content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void editarPerfilDeUsuarioInexistenteDevuelve404() throws Exception {
        tokenDelUsuario(99L);
        when(updateUserProfile.updateProfile(eq(99L), any()))
                .thenThrow(new UserNotFoundException("Usuario no encontrado: 99"));

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON).content(PERFIL_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.service").value("auth-service"));
    }

    /** Telefono que ya usa otro usuario. Mismo 409 generico que el alta, sin
     *  decir que campo choco. */
    @Test
    void editarPerfilConTelefonoDeOtroUsuarioDevuelve409() throws Exception {
        tokenDelUsuario(1L);
        when(updateUserProfile.updateProfile(eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"users_phone_key\""));

        mvc.perform(put("/auth/profile").header("Authorization", "Bearer un-token")
                        .contentType(APPLICATION_JSON).content(PERFIL_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Ya existe un registro con esos datos"));
    }
}
