package cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stub helpers for Catalog Service mock (contracts C-1, C-2, C-3).
 *
 * <p>All methods register stubs on the provided WireMockExtension instance.</p>
 */
public final class MockCatalogServer {

    private static final String API_KEY = "test-internal-key";

    private MockCatalogServer() {
    }

    // ── C-1: GET /api/internal/products?ids={ids} ──────────────────────

    public static void stubGetProductsByIdsOk(WireMockExtension wm, String productsJson) {
        wm.stubFor(get(urlPathMatching("/api/internal/products"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("ids", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(productsJson)));
    }

    public static void stubGetProductsByIdsOkSingle(WireMockExtension wm, long id, long restaurantId,
                                                     String name, String description, String image,
                                                     double price, boolean available) {
        String json = String.format(
                "[{\"id\":%d,\"restaurantId\":%d,\"name\":\"%s\",\"description\":\"%s\",\"image\":\"%s\",\"price\":%s,\"available\":%s}]",
                id, restaurantId, name, description, image, String.valueOf((long) price), available);
        stubGetProductsByIdsOk(wm, json);
    }

    public static void stubGetProductsByIdsEmpty(WireMockExtension wm) {
        wm.stubFor(get(urlPathMatching("/api/internal/products"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("ids", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    public static void stubGetProductsByIdsServerError(WireMockExtension wm) {
        wm.stubFor(get(urlPathMatching("/api/internal/products"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("ids", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Catalog unavailable\"}")));
    }

    // ── C-2: GET /api/internal/restaurants/{id} ────────────────────────

    public static void stubGetRestaurantByIdOk(WireMockExtension wm, long id, String name, String address, long userId) {
        wm.stubFor(get(urlEqualTo("/api/internal/restaurants/" + id))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":\"%s\",\"address\":\"%s\",\"userId\":%d}",
                                id, name, address, userId))));
    }

    public static void stubGetRestaurantByIdNotFound(WireMockExtension wm, long id) {
        wm.stubFor(get(urlEqualTo("/api/internal/restaurants/" + id))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"Restaurant not found\"}")));
    }

    public static void stubGetRestaurantByIdServerError(WireMockExtension wm, long id) {
        wm.stubFor(get(urlEqualTo("/api/internal/restaurants/" + id))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Catalog unavailable\"}")));
    }

    // ── C-3: GET /api/internal/restaurants?userId={userId} ─────────────

    public static void stubGetRestaurantsByUserIdOk(WireMockExtension wm, long userId, long restaurantId,
                                                     String name, String address) {
        String json = String.format(
                "[{\"id\":%d,\"name\":\"%s\",\"address\":\"%s\",\"userId\":%d}]",
                restaurantId, name, address, userId);
        wm.stubFor(get(urlPathMatching("/api/internal/restaurants"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)));
    }

    public static void stubGetRestaurantsByUserIdEmpty(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlPathMatching("/api/internal/restaurants"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    public static void stubGetRestaurantsByUserIdNotFound(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlPathMatching("/api/internal/restaurants"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"No restaurant for user\"}")));
    }

    public static void stubGetRestaurantsByUserIdServerError(WireMockExtension wm, long userId) {
        wm.stubFor(get(urlPathMatching("/api/internal/restaurants"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withQueryParam("userId", equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"Catalog unavailable\"}")));
    }
}
