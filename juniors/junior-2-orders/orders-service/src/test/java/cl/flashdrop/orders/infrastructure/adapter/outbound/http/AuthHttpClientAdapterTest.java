package cl.flashdrop.orders.infrastructure.adapter.outbound.http;

import cl.flashdrop.orders.domain.model.UserInfo;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock.MockAuthServer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class AuthHttpClientAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AuthHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .defaultHeader("Accept", "application/json")
                .build();
        adapter = new AuthHttpClientAdapter(restClient);
    }

    @Test
    void shouldReturnUserInfoWhenUserExists() {
        UUID userId = toUuid(42L);
        MockAuthServer.stubGetUserOk(wireMock, 42L, "Felipe", "Pousa", "felipe@example.com", "+56911111111");

        Optional<UserInfo> result = adapter.findUserById(userId);

        assertTrue(result.isPresent());
        assertEquals("Felipe Pousa", result.get().getFullName());
        assertEquals("felipe@example.com", result.get().getEmail());
        assertEquals("+56911111111", result.get().getPhone());
    }

    @Test
    void shouldComposeFullNameFromNameOnlyWhenLastNameIsNull() {
        UUID userId = toUuid(43L);
        MockAuthServer.stubGetUserOk(wireMock, 43L, "Felipe", null, "felipe@example.com", "+56911111111");

        Optional<UserInfo> result = adapter.findUserById(userId);

        assertTrue(result.isPresent());
        assertEquals("Felipe", result.get().getFullName());
        assertFalse(result.get().getFullName().contains("null"));
    }

    @Test
    void shouldComposeFullNameFromLastNameOnlyWhenNameIsNull() {
        UUID userId = toUuid(44L);
        MockAuthServer.stubGetUserOk(wireMock, 44L, null, "Pousa", "felipe@example.com", "+56911111111");

        Optional<UserInfo> result = adapter.findUserById(userId);

        assertTrue(result.isPresent());
        assertEquals("Pousa", result.get().getFullName());
        assertFalse(result.get().getFullName().contains("null"));
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        UUID userId = toUuid(999L);
        MockAuthServer.stubGetUserNotFound(wireMock, 999L);

        Optional<UserInfo> result = adapter.findUserById(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnServerError() {
        UUID userId = toUuid(42L);
        MockAuthServer.stubGetUserServerError(wireMock, 42L);

        var ex = assertThrows(cl.flashdrop.orders.infrastructure.exception.ExternalServiceException.class,
                () -> adapter.findUserById(userId));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Auth error"));
    }

    @Test
    void shouldReturnEmptyWhenUserIdIsNull() {
        Optional<UserInfo> result = adapter.findUserById(null);
        assertTrue(result.isEmpty());
    }

    private static UUID toUuid(long id) {
        return new UUID(0L, id);
    }
}
