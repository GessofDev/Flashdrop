package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.auth.application.dto.AdminUserResponse;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.AdminUserUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.AccessForbiddenException;
import com.flashdrop.auth.domain.exception.InvalidRoleException;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;

class AdminUserControllerTest {

    private static final TokenClaims ADMIN = new TokenClaims(4L, "admin@demo.cl",
            List.of("Cliente", "Restaurante", "Repartidor"));

    private final ValidateTokenUseCase validate = mock(ValidateTokenUseCase.class);
    private final AdminUserUseCase admin = mock(AdminUserUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new AdminUserController(validate, admin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(validate.validate("admin-token")).thenReturn(ADMIN);
    }

    @Test
    void findByEmail_returnsTheUserWithRoles() throws Exception {
        when(admin.findByEmail(ADMIN, "nuevo@correo.cl"))
                .thenReturn(new AdminUserResponse(9L, "Ana", "Perez", "nuevo@correo.cl", List.of("Cliente")));

        mvc.perform(get("/auth/admin/users").param("email", "nuevo@correo.cl")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.roles[0]").value("Cliente"))
                .andExpect(jsonPath("$.rut").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void withoutBearerToken_isUnauthorized() throws Exception {
        mvc.perform(get("/auth/admin/users").param("email", "x@y.cl"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidToken_isUnauthorized() throws Exception {
        when(validate.validate("bad")).thenThrow(new InvalidTokenException());

        mvc.perform(get("/auth/admin/users").param("email", "x@y.cl").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdmin_isForbidden() throws Exception {
        when(admin.findByEmail(any(), any())).thenThrow(new AccessForbiddenException("Se requiere un usuario administrador"));

        mvc.perform(get("/auth/admin/users").param("email", "x@y.cl").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownEmail_isNotFound() throws Exception {
        when(admin.findByEmail(any(), any())).thenThrow(new UserNotFoundException("User not found"));

        mvc.perform(get("/auth/admin/users").param("email", "x@y.cl").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignRoles_returnsTheNewRoles() throws Exception {
        when(admin.assignRoles(eq(ADMIN), eq(9L), eq(List.of("Cliente", "Restaurante")), any()))
                .thenReturn(new AssignUserRolesResult(9L, List.of(new InternalRoleResponse(2L, "Restaurante"))));

        mvc.perform(put("/auth/admin/users/9/roles").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"Cliente\",\"Restaurante\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(9))
                .andExpect(jsonPath("$.roles[0].name").value("Restaurante"));
    }

    @Test
    void assignRoles_notAssignableRole_isBadRequest() throws Exception {
        when(admin.assignRoles(any(), any(), any(), any())).thenThrow(new InvalidRoleException("Rol no asignable"));

        mvc.perform(put("/auth/admin/users/9/roles").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"Soporte\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignRoles_onOneself_isForbidden() throws Exception {
        when(admin.assignRoles(any(), any(), any(), any()))
                .thenThrow(new AccessForbiddenException("Un administrador no puede cambiar sus propios roles"));

        mvc.perform(put("/auth/admin/users/4/roles").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"Cliente\"]}"))
                .andExpect(status().isForbidden());
    }
}
