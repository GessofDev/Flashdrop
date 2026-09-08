package cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stub helpers for Auth Service mock (contract C-4).
 *
 * <p>All methods register stubs on the provided WireMockExtension instance.</p>
 */
public final class MockAuthServer {

    private static final String API_KEY = "test-internal-key";
    private static final String BASE = "/api/internal/users";

    private MockAuthServer() {
    }

    /**
     * Registra el stub OK con el contrato real de Auth (C-4, {@code MIGRATION_PLAN.md}
     * sección 8.1): {@code name} y {@code lastName} por separado, nunca {@code fullName}.
     */
    public static void stubGetUserOk(WireMockExtension wm, long userId, String name, String lastName, String email, String phone) {
        wm.stubFor(get(urlEqualTo(BASE + "/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":%s,\"lastName\":%s,\"email\":\"%s\",\"phone\":\"%s\"}",
                                userId, jsonStringOrNull(name), jsonStringOrNull(lastName), email, phone))));
    }

    /**
     * {@code "valor"} si no es null, o el literal JSON {@code null} (sin comillas) si lo es
     * — para no confundir un {@code name}/{@code lastName} ausente con el string {@code "null"}.
     */
    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    public static void stubGetUserNotFound(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlEqualTo(BASE + "/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"User not found\"}")));
    }

    public static void stubGetUserServerError(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlEqualTo(BASE + "/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Auth unavailable\"}")));
    }

    public static void stubGetUserInvalidApiKey(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlEqualTo(BASE + "/" + userId))
                .withHeader("X-Internal-Api-Key", notMatching(API_KEY))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}")));
    }
}
