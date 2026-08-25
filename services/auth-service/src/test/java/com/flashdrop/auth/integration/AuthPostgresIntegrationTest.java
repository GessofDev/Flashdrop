package com.flashdrop.auth.integration;

import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.model.RefreshToken;
import com.flashdrop.auth.domain.valueobject.Email;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el perfil `postgres` contra un PostgreSQL real y efimero.
 *
 * <p>Es el unico test que toca la base. Los otros 46 mockean los puertos de
 * salida, y por eso no detectaron dos defectos reales de la ruta de registro
 * que solo apareciron al ejecutar contra un motor de verdad. Este cubre esa
 * frontera: migraciones, mapeo de entidades y el flujo completo de alta.
 *
 * <p>Se desactiva solo si no hay Docker, asi que no rompe el build en
 * entornos que no pueden levantar contenedores.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class AuthPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.allow-ephemeral-key", () -> true);
        registry.add("services.internal-api-key", () -> "clave-interna-de-prueba");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CredentialStore credentials;
    @Autowired RoleRepository roles;
    @Autowired RefreshTokenStore refreshTokens;

    // ------------------------------------------------------------ migraciones

    @Test
    void flywayCreaLasCincoTablasPropietarias() {
        List<String> tablas = jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_name <> 'flyway_schema_history' "
                        + "order by table_name", String.class);

        assertEquals(List.of("login", "refresh_tokens", "roles", "user_has_roles", "users"), tablas);
    }

    @Test
    void ningunaTablaDeOtroMicroservicioViveEnEstaBase() {
        List<String> ajenas = jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_name in "
                        + "('orders','order_items','client','categories','products',"
                        + "'restaurant','delivery','delivery_routes')", String.class);

        assertTrue(ajenas.isEmpty(),
                "La base de auth no debe contener tablas de otros servicios: " + ajenas);
    }

    @Test
    void elSeedCargaLosDatosDemoConSusIdsFijos() {
        assertEquals(5, jdbc.queryForObject("select count(*) from users", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from roles", Integer.class));
        assertEquals(7, jdbc.queryForObject("select count(*) from user_has_roles", Integer.class));
        assertEquals("cliente@demo.cl",
                jdbc.queryForObject("select email from users where id = 1", String.class));
    }

    /**
     * Los INSERT del seed llevan id explicito y no avanzan la secuencia. Sin
     * el bloque setval, el primer alta chocaria con 23505.
     */
    @Test
    void lasSecuenciasQuedanSincronizadasConElSeed() {
        assertEquals(5, jdbc.queryForObject("select last_value from users_id_seq", Long.class));
    }

    // -------------------------------------------------------------- adaptadores

    @Test
    void elAdaptadorResuelveLosRolesPorLaTablaPuente() {
        var admin = users.findById(4L).orElseThrow();

        assertEquals(List.of("Cliente", "Restaurante", "Repartidor"),
                admin.roleNames().stream().sorted(
                        java.util.Comparator.comparing(
                                n -> List.of("Cliente", "Restaurante", "Repartidor").indexOf(n))).toList());
    }

    @Test
    void elBatchNoTraeRolesYRespetaElOrdenDeIds() {
        var encontrados = users.findAllByIds(List.of(3L, 1L, 999L));

        assertEquals(2, encontrados.size());
        assertEquals(1L, encontrados.get(0).id());
        assertEquals(3L, encontrados.get(1).id());
        assertTrue(encontrados.get(0).roles().isEmpty());
    }

    @Test
    void lasCredencialesSeGuardanYRecuperanConSuEstado() {
        var rol = roles.findByName("Cliente").orElseThrow();
        assertNotNull(rol.id());

        var creado = users.save(new com.flashdrop.auth.domain.model.User(
                null, new Email("adaptador@test.cl"), null, "Adaptador", "Test",
                "+56900001111", null, List.of(rol), Instant.now()));
        assertNotNull(creado.id());

        credentials.save(new com.flashdrop.auth.domain.model.Credentials(
                null, creado.id(), "adaptador@test.cl", "$2b$10$hash", "ACTIVE"));

        var leidas = credentials.findByLogin("adaptador@test.cl").orElseThrow();
        assertEquals(creado.id(), leidas.userId());
        assertTrue(leidas.isActive());
    }

    @Test
    void elRefreshTokenSeGuardaYSeRevoca() {
        refreshTokens.save(new RefreshToken(null, 1L, "hash-de-prueba",
                Instant.now().plusSeconds(3600), false));

        var guardado = refreshTokens.findByTokenHash("hash-de-prueba").orElseThrow();
        assertTrue(guardado.isUsable(Instant.now()));

        refreshTokens.revoke(guardado);
        assertTrue(refreshTokens.findByTokenHash("hash-de-prueba").orElseThrow().revoked());
    }

    // ------------------------------------------------------------------- flujo

    /**
     * El alta escribe en users, login y user_has_roles de una pasada. Es el
     * camino que estaba roto dos veces contra PostgREST y el que ningun test
     * con mocks podia cubrir.
     */
    @Test
    void registroYLoginFuncionanDePuntaAPunta() throws Exception {
        var alta = """
                {"email":"e2e@flashdrop.cl","password":"Segura1234",
                 "name":"E2E","lastName":"Test","phone":"+56922223333"}
                """;

        mvc.perform(post("/auth/register").contentType("application/json").content(alta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber());

        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("""
                                {"login":"e2e@flashdrop.cl","password":"Segura1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("Cliente"));
    }

    /** El usuario nuevo no puede reutilizar un id del seed. */
    @Test
    void elUsuarioNuevoTomaUnIdPosteriorAlSeed() {
        var rol = roles.findByName("Cliente").orElseThrow();
        var creado = users.save(new com.flashdrop.auth.domain.model.User(
                null, new Email("secuencia@test.cl"), null, "Sec", "Uencia",
                "+56900002222", null, List.of(rol), Instant.now()));

        assertTrue(creado.id() > 5,
                "El id deberia venir despues del seed, vino " + creado.id());
    }
}
