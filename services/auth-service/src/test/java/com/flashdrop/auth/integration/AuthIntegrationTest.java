package com.flashdrop.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba end-to-end del flujo completo (HTTP + Spring Security + Flyway + JPA)
 * contra un PostgreSQL real en contenedor. Se desactiva automáticamente si no
 * hay Docker disponible, por lo que no rompe el build en entornos sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.allow-ephemeral-key", () -> true);   // clave RSA efímera solo para el test
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void registroYLoginDevuelvenTokens() {
        var register = Map.of("email", "nuevo@flashdrop.cl", "password", "Segura1234",
                "name", "Nuevo", "lastName", "Cliente", "phone", "+56911112222");
        var registerResponse = rest.postForEntity("/auth/register", register, Map.class);
        assertEquals(201, registerResponse.getStatusCode().value());

        var login = Map.of("login", "nuevo@flashdrop.cl", "password", "Segura1234");
        var loginResponse = rest.postForEntity("/auth/login", login, Map.class);
        assertEquals(200, loginResponse.getStatusCode().value());
        assertNotNull(loginResponse.getBody().get("accessToken"));
        assertNotNull(loginResponse.getBody().get("refreshToken"));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401YCodigoTipado() {
        var login = Map.of("login", "noexiste@flashdrop.cl", "password", "loquesea123");
        var response = rest.postForEntity("/auth/login", login, Map.class);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("AUTH_INVALID_CREDENTIALS", response.getBody().get("code"));
    }
}
