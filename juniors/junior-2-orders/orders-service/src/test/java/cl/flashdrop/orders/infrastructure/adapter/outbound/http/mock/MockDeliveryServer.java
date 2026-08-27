package cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stub helpers for Delivery Service mock (contracts C-5, C-6, C-7).
 *
 * <p>All methods register stubs on the provided WireMockExtension instance.</p>
 */
public final class MockDeliveryServer {

    private static final String API_KEY = "test-internal-key";

    private MockDeliveryServer() {
    }

    // ── C-5: GET /api/internal/delivery/by-user/{userId} ───────────────

    public static void stubGetDeliveryByUserIdOk(WireMockExtension wm, long userId, long deliveryId,
                                                  String fullName, String phone) {
        wm.stubFor(get(urlEqualTo("/api/internal/delivery/by-user/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"fullName\":\"%s\",\"phone\":\"%s\"}",
                                deliveryId, fullName, phone))));
    }

    public static void stubGetDeliveryByUserIdNotFound(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlEqualTo("/api/internal/delivery/by-user/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"Delivery person not found\"}")));
    }

    public static void stubGetDeliveryByUserIdServerError(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlEqualTo("/api/internal/delivery/by-user/" + userId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }

    // ── C-6: POST /api/internal/delivery/routes ────────────────────────

    public static void stubCreateRouteOk(WireMockExtension wm) {
        wm.stubFor(post(urlEqualTo("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(201)));
    }

    public static void stubCreateRouteServerError(WireMockExtension wm) {
        wm.stubFor(post(urlEqualTo("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }

    // ── C-7: PATCH /api/internal/delivery/routes/order/{orderId} ───────

    public static void stubUpdateRouteByOrderOk(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathMatching("/api/internal/delivery/routes/order/" + orderId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(204)));
    }

    public static void stubUpdateRouteByOrderNotFound(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathMatching("/api/internal/delivery/routes/order/" + orderId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"Route not found\"}")));
    }

    public static void stubUpdateRouteByOrderServerError(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathMatching("/api/internal/delivery/routes/order/" + orderId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }

    // ── C-7 bulk: PATCH /api/internal/delivery/routes ──────────────────

    public static void stubUpdateRoutesBulkOk(WireMockExtension wm) {
        wm.stubFor(patch(urlPathMatching("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(204)));
    }

    public static void stubUpdateRoutesBulkServerError(WireMockExtension wm) {
        wm.stubFor(patch(urlPathMatching("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }
}
