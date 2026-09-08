package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.InternalUserResponse;
import com.flashdrop.auth.application.port.inbound.GetInternalUserUseCase;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.infrastructure.config.InternalApiKeyFilter;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InternalUserControllerTest {

    private static final String API_KEY = "test-internal-key";

    private final GetInternalUserUseCase useCase = mock(GetInternalUserUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new InternalUserController(useCase);
        var filter = new InternalApiKeyFilter(API_KEY, objectMapper);
        mvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @Test
    void usuarioExistenteRetorna200ConContratoExacto() throws Exception {
        when(useCase.getUser(1L)).thenReturn(new InternalUserResponse(
                1L, "Cliente", "Demo", "cliente@demo.cl", "+56911111111"));

        MvcResult result = mvc.perform(get("/api/internal/users/1")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Cliente"))
                .andExpect(jsonPath("$.lastName").value("Demo"))
                .andExpect(jsonPath("$.email").value("cliente@demo.cl"))
                .andExpect(jsonPath("$.phone").value("+56911111111"))
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        assertEquals(List.of("email", "id", "lastName", "name", "phone"),
                body.keySet().stream().map(Object::toString).sorted().toList());
    }

    @Test
    void batchRetornaArrayConElContratoExacto() throws Exception {
        when(useCase.getUsers(List.of(1L, 3L))).thenReturn(List.of(
                new InternalUserResponse(1L, "Cliente", "Demo", "cliente@demo.cl", "+56911111111"),
                new InternalUserResponse(3L, "Repartidor", "Demo", "repartidor@demo.cl", "+56933333333")));

        MvcResult result = mvc.perform(get("/api/internal/users")
                        .param("ids", "1,3")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].email").value("repartidor@demo.cl"))
                .andReturn();

        // Mismo contrato que el endpoint unitario: ni un campo de más.
        List<?> body = objectMapper.readValue(result.getResponse().getContentAsByteArray(), List.class);
        Map<?, ?> primero = (Map<?, ?>) body.get(0);
        assertEquals(List.of("email", "id", "lastName", "name", "phone"),
                primero.keySet().stream().map(Object::toString).sorted().toList());
    }

    @Test
    void batchConIdsInexistentesRetornaArrayVacio() throws Exception {
        when(useCase.getUsers(List.of(999L))).thenReturn(List.of());

        mvc.perform(get("/api/internal/users")
                        .param("ids", "999")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void batchSinParametroIdsRetornaArrayVacio() throws Exception {
        when(useCase.getUsers(List.of())).thenReturn(List.of());

        mvc.perform(get("/api/internal/users")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void batchConIdsNoNumericosRetorna400EnElFormatoDelPlan() throws Exception {
        mvc.perform(get("/api/internal/users")
                        .param("ids", "abc")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                // Los endpoints internos NO usan el ApiError de observabilidad.
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void batchSinApiKeyRetorna403() throws Exception {
        mvc.perform(get("/api/internal/users").param("ids", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioInexistenteRetorna404Estructurado() throws Exception {
        when(useCase.getUser(99L))
                .thenThrow(new UserNotFoundException("User not found with id: 99"));

        mvc.perform(get("/api/internal/users/99")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"status":404,"error":"NOT_FOUND","message":"User not found with id: 99"}
                        """, true));
    }

    @Test
    void rolesAsignadosRetornanLista() throws Exception {
        when(useCase.getRoles(1L)).thenReturn(List.of(
                new InternalRoleResponse(1L, "Cliente"),
                new InternalRoleResponse(2L, "Restaurante")));

        mvc.perform(get("/api/internal/users/1/roles")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":1,"name":"Cliente"},{"id":2,"name":"Restaurante"}]
                        """, true));
    }

    @Test
    void usuarioSinRolesRetornaListaVacia() throws Exception {
        when(useCase.getRoles(2L)).thenReturn(List.of());

        mvc.perform(get("/api/internal/users/2/roles")
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void apiKeyAusenteRetorna403() throws Exception {
        mvc.perform(get("/api/internal/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("""
                        {"status":403,"error":"FORBIDDEN","message":"Invalid internal API key"}
                        """, true));
    }

    @Test
    void apiKeyIncorrectaRetorna403() throws Exception {
        mvc.perform(get("/api/internal/users/1")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("""
                        {"status":403,"error":"FORBIDDEN","message":"Invalid internal API key"}
                        """, true));
    }
}
