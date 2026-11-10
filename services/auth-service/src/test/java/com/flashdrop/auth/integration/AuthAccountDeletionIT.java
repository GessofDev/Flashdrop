package com.flashdrop.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.auth.application.port.inbound.ProcessAccountDeletionUseCase;
import com.sun.net.httpserver.HttpServer;

/**
 * F5 de punta a punta dentro de Auth, contra PostgreSQL real y con Orders, Delivery y Catalog simulados por
 * servidores HTTP locales: rechazo mientras haya pedidos en curso, cuenta inhabilitada al aceptar, reintento
 * tras un fallo, aborto si aparecen pedidos, y borrado en cascada de todos los datos del usuario.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class AuthAccountDeletionIT {

    private static final String API_KEY = "clave-interna-de-prueba";
    private static final String PASSWORD = "Segura1234";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final FakeService ORDERS = new FakeService();
    static final FakeService DELIVERY = new FakeService();
    static final FakeService CATALOG = new FakeService();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
        registry.add("jwt.allow-ephemeral-key", () -> true);
        registry.add("services.internal-api-key", () -> API_KEY);
        registry.add("services.orders-url", ORDERS::url);
        registry.add("services.delivery-url", DELIVERY::url);
        registry.add("services.catalog-url", CATALOG::url);
        // Se procesa a mano para no competir con el planificador.
        registry.add("auth.account-deletion.enabled", () -> false);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProcessAccountDeletionUseCase process;

    @BeforeEach
    void limpiarSimulados() {
        ORDERS.reset();
        DELIVERY.reset();
        CATALOG.reset();
    }

    @Test
    void conPedidosEnCursoNoSePuedeEliminar_noSeCancelaNada_yLaCuentaSigueFuncionando() throws Exception {
        long id = alta("bloqueado@flashdrop.cl");
        String token = login("bloqueado@flashdrop.cl");
        ORDERS.blockersBody = "{\"blocked\":true,\"asClient\":1,\"asRestaurant\":0,\"asCourier\":2}";

        mvc.perform(eliminar(token, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_ACTIVE_PROCESSES"));

        assertEquals(0, filas("account_deletions", id));
        assertEquals(1, jdbc.queryForObject("select status from login where id_users = ?", Integer.class, id));
        assertEquals(List.of(), ORDERS.erasures, "no se borra nada mientras haya pedidos en curso");
        login("bloqueado@flashdrop.cl");
    }

    @Test
    void conLaContrasenaIncorrectaSeRechazaYNoCambiaNada() throws Exception {
        alta("clavemala@flashdrop.cl");
        String token = login("clavemala@flashdrop.cl");

        mvc.perform(eliminar(token, "otra-clave"))
                .andExpect(status().isUnauthorized());

        assertEquals(1, jdbc.queryForObject("select count(*) from users where email = ?", Integer.class,
                "clavemala@flashdrop.cl"));
        assertEquals(List.of(), ORDERS.calls);
    }

    @Test
    void sinTokenSeRechaza() throws Exception {
        mvc.perform(delete("/auth/account").contentType("application/json")
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laEliminacionSeReintentaTrasUnFalloYAlFinalNoQuedaNingunDatoDelUsuario() throws Exception {
        long id = alta("eliminar@flashdrop.cl");
        String token = login("eliminar@flashdrop.cl");
        String refresh = loginRefresh("eliminar@flashdrop.cl");
        jdbc.update("insert into password_reset_tokens (user_id, token_hash, expires_at) "
                + "values (?, 'hash-de-prueba', now() + interval '1 hour')", id);
        ORDERS.eraseStatuses.add(500);

        // Se acepta: la cuenta queda inhabilitada y sin sesiones al instante.
        mvc.perform(eliminar(token, PASSWORD)).andExpect(status().isAccepted());
        assertEquals(1, filas("account_deletions", id));
        assertEquals(0, jdbc.queryForObject("select status from login where id_users = ?", Integer.class, id));
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"login\":\"eliminar@flashdrop.cl\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Primer intento: Orders responde 500 -> nada se pierde y se agenda el reintento.
        assertEquals(0, process.processDue());
        assertEquals(1, filas("account_deletions", id));
        assertEquals(1, jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select attempts from account_deletions where user_id = ?",
                Integer.class, id));
        assertEquals(List.of(), DELIVERY.erasures, "Delivery no se toca hasta que Orders termine");

        // Aun no vence el reintento: no se hace nada.
        assertEquals(0, process.processDue());
        assertEquals(1, ORDERS.erasures.size());

        // Vence el reintento: se completa Orders, Delivery y Catalog y se borra el usuario.
        jdbc.update("update account_deletions set next_attempt_at = now() - interval '1 second' where user_id = ?", id);
        assertEquals(1, process.processDue());

        assertEquals(List.of("/api/internal/users/" + id + "/erase", "/api/internal/users/" + id + "/erase"),
                ORDERS.erasures);
        assertEquals(List.of("/api/internal/erasure/delivery-persons?userId=" + id), DELIVERY.erasures);
        assertEquals(List.of("/api/internal/erasure/restaurant-owner?userId=" + id), CATALOG.erasures);
        assertTrue(ORDERS.keysSeen.stream().allMatch(API_KEY::equals));
        assertEquals(0, filas("users", id, "id"));
        assertEquals(0, filas("login", id, "id_users"));
        assertEquals(0, filas("user_has_roles", id, "id_user"));
        assertEquals(0, filas("refresh_tokens", id, "user_id"));
        assertEquals(0, filas("password_reset_tokens", id, "user_id"));
        assertEquals(0, filas("account_deletions", id));
        assertEquals(0, jdbc.queryForObject("select count(*) from users where email = ?", Integer.class,
                "eliminar@flashdrop.cl"));
    }

    @Test
    void siAparecenPedidosEnCursoAntesDeBorrarSeAbortaYSeDevuelveLaCuenta() throws Exception {
        long id = alta("carrera@flashdrop.cl");
        String token = login("carrera@flashdrop.cl");
        ORDERS.eraseStatuses.add(409);

        mvc.perform(eliminar(token, PASSWORD)).andExpect(status().isAccepted());
        assertEquals(0, process.processDue());

        assertEquals(0, filas("account_deletions", id));
        assertEquals(1, jdbc.queryForObject("select status from login where id_users = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, id));
        assertEquals(List.of(), DELIVERY.erasures);
        login("carrera@flashdrop.cl");
    }

    @Test
    void siOrdersNoRespondeAlPedirLaEliminacionSeResponde503YLaCuentaNoSeToca() throws Exception {
        long id = alta("caido@flashdrop.cl");
        String token = login("caido@flashdrop.cl");
        ORDERS.blockersStatus = 503;

        mvc.perform(eliminar(token, PASSWORD)).andExpect(status().isServiceUnavailable());

        assertEquals(0, filas("account_deletions", id));
        assertEquals(1, jdbc.queryForObject("select status from login where id_users = ?", Integer.class, id));
    }

    // ------------------------------------------------------------------ ayudas

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder eliminar(
            String token, String password) {
        return delete("/auth/account").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"password\":\"" + password + "\"}");
    }

    private long alta(String email) throws Exception {
        mvc.perform(post("/auth/register").contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s","name":"Alta","lastName":"Borrado"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private String login(String email) throws Exception {
        return loginJson(email).get("accessToken").asText();
    }

    private String loginRefresh(String email) throws Exception {
        return loginJson(email).get("refreshToken").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode loginJson(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    private int filas(String table, long userId) {
        return filas(table, userId, "user_id");
    }

    private int filas(String table, long userId, String column) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?",
                Integer.class, userId);
    }

    /** Servicio interno simulado: registra llamadas y responde lo que cada prueba configure. */
    static final class FakeService {
        final HttpServer server;
        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<String> erasures = new CopyOnWriteArrayList<>();
        final List<String> keysSeen = new CopyOnWriteArrayList<>();
        final Deque<Integer> eraseStatuses = new ArrayDeque<>();
        volatile String blockersBody;
        volatile int blockersStatus;

        FakeService() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().toString();
                calls.add(path);
                keysSeen.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")));
                int status = 200;
                String body = "{}";
                if (path.endsWith("/deletion-blockers")) {
                    status = blockersStatus == 0 ? 200 : blockersStatus;
                    body = blockersBody;
                } else {
                    erasures.add(path);
                    Integer next = eraseStatuses.poll();
                    if (next != null) {
                        status = next;
                    }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            reset();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            calls.clear();
            erasures.clear();
            keysSeen.clear();
            eraseStatuses.clear();
            blockersStatus = 0;
            blockersBody = "{\"blocked\":false,\"asClient\":0,\"asRestaurant\":0,\"asCourier\":0}";
        }
    }
}
