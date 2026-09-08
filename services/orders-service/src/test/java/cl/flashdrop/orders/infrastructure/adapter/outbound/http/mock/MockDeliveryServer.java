package cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stub helpers for Delivery Service mock (contracts C-5, C-6, C-7).
 *
 * <p>URLs match the real delivery-service controllers
 * ({@code InternalDeliveryPersonsController}, {@code InternalRoutesController}),
 * verified live against Postgres real (Floci) on 2026-09-01 — see
 * {@code TEAM_INTERNAL_API_CONTRACTS.md} §9 (GAP-01).</p>
 *
 * <p>All methods register stubs on the provided WireMockExtension instance.</p>
 */
public final class MockDeliveryServer {

    private static final String API_KEY = "test-internal-key";

    private MockDeliveryServer() {
    }

    // ── C-5: GET /api/internal/delivery-persons?userId={userId} ────────
    // Response is wrapped in ApiResponse<DeliveryPersonResponse>: {success, message, data}.

    public static void stubGetDeliveryByUserIdOk(WireMockExtension wm, long userId, long deliveryId,
                                                  String vehicle) {
        wm.stubFor(get(urlPathEqualTo("/api/internal/delivery-persons"))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"success\":true,\"message\":null,\"data\":{\"id\":%d,\"userId\":\"%d\",\"vehicle\":\"%s\"}}",
                                deliveryId, userId, vehicle))));
    }

    public static void stubGetDeliveryByUserIdNotFound(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlPathEqualTo("/api/internal/delivery-persons"))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"Delivery person not found\"}")));
    }

    public static void stubGetDeliveryByUserIdServerError(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlPathEqualTo("/api/internal/delivery-persons"))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }

    // ── C-6: POST /api/internal/routes ──────────────────────────────────

    public static void stubCreateRouteOk(WireMockExtension wm) {
        wm.stubFor(post(urlEqualTo("/api/internal/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(201)));
    }

    public static void stubCreateRouteServerError(WireMockExtension wm) {
        wm.stubFor(post(urlEqualTo("/api/internal/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }

    // ── C-7: PATCH /api/internal/routes/order/{orderId}/status ─────────
    // GAP-01, resuelto en delivery-service commit 904464d. No hay variante
    // bulk: updateRouteStatus (Orders) llama a este mismo endpoint una vez
    // por orderId.

    public static void stubUpdateRouteByOrderOk(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathEqualTo("/api/internal/routes/order/" + orderId + "/status"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":null,\"data\":{}}")));
    }

    public static void stubUpdateRouteByOrderNotFound(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathEqualTo("/api/internal/routes/order/" + orderId + "/status"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"message\":\"No route found for orderId=" + orderId + "\",\"data\":null}")));
    }

    public static void stubUpdateRouteByOrderServerError(WireMockExtension wm, long orderId) {
        wm.stubFor(patch(urlPathEqualTo("/api/internal/routes/order/" + orderId + "/status"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Delivery unavailable\"}")));
    }
}
