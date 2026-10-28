package com.flashdrop.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de asignacion de roles contra PostgreSQL real.
 *
 * <p>Es el camino que recorre QA: alta, asignacion de roles por el endpoint
 * interno y login con esos roles ya en el token.
 *
 * <p>Va en su propia clase, con su propio contenedor, a proposito: la otra
 * clase de integracion comparte una base sembrada y asserta conteos exactos
 * (usuarios, asignaciones y secuencia). Crear usuarios ahi dejaria esos
 * conteos movidos segun el orden de ejecucion de los tests.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class AuthRoleAssignmentIT {

    private static final String API_KEY = "clave-interna-de-prueba";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // El seed trae el catalogo de roles (Cliente, Restaurante, Repartidor).
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/seed");
        registry.add("jwt.allow-ephemeral-key", () -> true);
        registry.add("services.internal-api-key", () -> API_KEY);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void elAltaNaceConClienteYElLoginTraeLosRolesAsignados() throws Exception {
        long id = alta("flujo@flashdrop.cl");

        // El registro siempre nace con el rol base.
        assertEquals(List.of("Cliente"), rolesEnBase(id));

        // Asignar reemplaza el conjunto completo y no deja el rol base de lado.
        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["Repartidor"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("Repartidor"));

        assertEquals(List.of("Repartidor"), rolesEnBase(id));

        // El token ya viaja con los roles nuevos.
        var login = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("""
                                {"login":"flujo@flashdrop.cl","password":"Segura1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("Repartidor"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(JsonPath.read(login, "$.roles").toString().contains("Repartidor"));
    }

    /** Un rol desconocido se rechaza con 400 y no toca el conjunto guardado. */
    @Test
    void unRolDesconocidoNoCambiaElConjunto() throws Exception {
        long id = alta("desconocido@flashdrop.cl");

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["Chef"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertEquals(List.of("Cliente"), rolesEnBase(id));
    }

    /** Reasignar un rol que ya estaba no choca con la restriccion unica. */
    @Test
    void reasignarUnRolQueYaEstabaAsignadoNoFalla() throws Exception {
        long id = alta("repetido@flashdrop.cl");

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["Cliente","Repartidor"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["Cliente","Repartidor"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        assertEquals(List.of("Cliente", "Repartidor"), rolesEnBase(id));
    }

    /** El endpoint sin la clave interna no mueve nada. */
    @Test
    void sinLaClaveInternaNoSeAsignanRoles() throws Exception {
        long id = alta("sinclave@flashdrop.cl");

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .contentType("application/json")
                        .content("""
                                {"roles":["Repartidor"]}
                                """))
                .andExpect(status().isForbidden());

        assertEquals(List.of("Cliente"), rolesEnBase(id));
    }

    /**
     * El conjunto vacio deja al usuario sin roles en la base, no solo en la
     * respuesta: es el unico camino que limpia las filas de la tabla puente.
     */
    @Test
    void unaListaVaciaDejaAlUsuarioSinRolesEnLaBase() throws Exception {
        long id = alta("sinroles@flashdrop.cl");
        assertEquals(List.of("Cliente"), rolesEnBase(id));

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(0));

        assertTrue(rolesEnBase(id).isEmpty(), "quedaron roles: " + rolesEnBase(id));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from user_has_roles where id_user = ?", Integer.class, id));
    }

    /** Un usuario que no existe: 404 y ninguna fila huerfana en la base. */
    @Test
    void unUsuarioInexistenteDevuelve404YNoDejaFilas() throws Exception {
        mvc.perform(put("/api/internal/users/999999/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["Repartidor"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from user_has_roles where id_user = ?", Integer.class, 999999L));
    }

    /**
     * El nombre que se persiste es el canonico de la tabla y el repetido se
     * guarda una sola vez, sin depender de mocks.
     */
    @Test
    void elNombreSeGuardaCanonicoYLosRepetidosUnaSolaVez() throws Exception {
        long id = alta("canonico@flashdrop.cl");

        mvc.perform(put("/api/internal/users/" + id + "/roles")
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"roles":["repartidor","REPARTIDOR","Repartidor"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("Repartidor"));

        assertEquals(List.of("Repartidor"), rolesEnBase(id));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from user_has_roles where id_user = ?", Integer.class, id));
    }

    private long alta(String email) throws Exception {
        mvc.perform(post("/auth/register").contentType("application/json")
                        .content("""
                                {"email":"%s","password":"Segura1234",
                                 "name":"Alta","lastName":"Roles"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        return jdbc.queryForObject(
                "select id from users where email = ?", Long.class, email);
    }

    private List<String> rolesEnBase(long userId) {
        return jdbc.queryForList(
                "select r.name from user_has_roles uhr "
                        + "join roles r on r.id = uhr.id_rol where uhr.id_user = ? order by r.id",
                String.class, userId);
    }
}
