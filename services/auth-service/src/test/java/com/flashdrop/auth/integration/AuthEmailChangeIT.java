package com.flashdrop.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.auth.application.port.outbound.EmailChangeMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class AuthEmailChangeIT {

    private static final String API_KEY = "clave-interna-de-prueba";
    private static final String PASSWORD = "Segura1234";
    private static final AtomicReference<String> LAST_LINK = new AtomicReference<>();
    private static final AtomicInteger PHONES = new AtomicInteger(100);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
        registry.add("jwt.allow-ephemeral-key", () -> true);
        registry.add("services.internal-api-key", () -> API_KEY);
        registry.add("auth.email-change.link-base-url", () -> "https://app.flashdrop.cl/email-change");
    }

    @TestConfiguration
    static class MailerCaptureConfig {
        @Bean
        @Primary
        EmailChangeMailer capturingEmailChangeMailer() {
            return new EmailChangeMailer() {
                @Override
                public void sendConfirmationLink(String currentEmail, String recipientName, String confirmLink, Duration validFor) {
                    LAST_LINK.set(confirmLink);
                }

                @Override
                public void sendEmailChangedNotice(String newEmail, String recipientName) {
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullEmailChangeFlow_updatesUsersAndLoginAndRevokesRefreshTokens() throws Exception {
        String email = "old-user@flashdrop.cl";
        long userId = registrar(email);
        String accessToken = accessToken(email);
        String refreshToken = refreshToken(email);

        mvc.perform(post("/auth/email/change-request")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isAccepted());

        mvc.perform(post("/auth/email/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenDelUltimoEnlace() + "\",\"newEmail\":\"new-user@flashdrop.cl\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"new-user@flashdrop.cl\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/internal/users/" + userId)
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-user@flashdrop.cl"));
    }

    /** El enlace es de un solo uso: el segundo intento con el mismo token se
     *  rechaza y la cuenta queda como estaba. */
    @Test
    void elEnlaceDeConfirmacionNoSePuedeReutilizar() throws Exception {
        String email = "replay-user@flashdrop.cl";
        registrar(email);

        mvc.perform(post("/auth/email/change-request")
                        .header("Authorization", "Bearer " + accessToken(email))
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isAccepted());

        String rawToken = tokenDelUltimoEnlace();

        mvc.perform(post("/auth/email/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newEmail\":\"replay-nuevo@flashdrop.cl\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/email/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newEmail\":\"otro@flashdrop.cl\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Un email ya registrado no se le puede quitar a su dueño: responde 409 y
     *  no deja el email a medias (la transacción hace rollback). */
    @Test
    void unEmailYaRegistradoNoSeRobaYNoDejaDatosPartidos() throws Exception {
        String email = "ladron@flashdrop.cl";
        registrar(email);

        mvc.perform(post("/auth/email/change-request")
                        .header("Authorization", "Bearer " + accessToken(email))
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isAccepted());

        String rawToken = tokenDelUltimoEnlace();

        mvc.perform(post("/auth/email/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newEmail\":\"cliente@demo.cl\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        // La cuenta sigue con su email original: no quedo nada a medias.
        mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    private long registrar(String email) throws Exception {
        String body = mvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "rut": "12.345.678-9",
                                  "name": "Test",
                                  "lastName": "User",
                                  "phone": "+5698765%04d"
                                }
                                """.formatted(email, PASSWORD, PHONES.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("userId").asLong();
    }

    private String accessToken(String email) throws Exception {
        return json.readTree(login(email)).get("accessToken").asText();
    }

    private String refreshToken(String email) throws Exception {
        return json.readTree(login(email)).get("refreshToken").asText();
    }

    private String login(String email) throws Exception {
        return mvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenDelUltimoEnlace() {
        String link = LAST_LINK.get();
        assertNotNull(link);
        return link.substring(link.indexOf("#token=") + "#token=".length());
    }
}
