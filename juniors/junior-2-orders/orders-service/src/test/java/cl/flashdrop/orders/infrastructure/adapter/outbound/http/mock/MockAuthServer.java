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

    public static void stubGetUserOk(WireMockExtension wm, long userId, String fullName, String email, String phone) {
        wm.stubFor(get(urlEqualTo(BASE + "/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}",
                                userId, fullName, email, phone))));
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
