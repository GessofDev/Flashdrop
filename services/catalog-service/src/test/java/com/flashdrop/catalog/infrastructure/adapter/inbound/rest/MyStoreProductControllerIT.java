package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "INTERNAL_API_KEY=dev-key",
        "S3_PUBLIC_URL_BASE=/catalog/images"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MyStoreProductControllerIT {

    private static final String IMAGE_KEY =
            "products/2026/10/550e8400-e29b-41d4-a716-446655440000.webp";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsRequireAuthenticationAndRestaurantRole() throws Exception {
        mockMvc.perform(get("/api/catalog/my/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/catalog/my/products")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDerivesRestaurantIdAndIgnoresBodyRestaurantId() throws Exception {
        mockMvc.perform(post("/api/catalog/my/products")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Producto owner", true, 2L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.name").value("Producto owner"))
                .andExpect(jsonPath("$.image").value("/catalog/images/" + IMAGE_KEY));
    }

    @Test
    void ownerListIncludesInactiveProducts() throws Exception {
        mockMvc.perform(post("/api/catalog/my/products")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Producto inactivo", false, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/catalog/my/products").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", hasItem("Producto inactivo")))
                .andExpect(jsonPath("$[2].available").value(false));
    }

    @Test
    void updateOwnProductSucceeds() throws Exception {
        String body = """
                {
                  "categoryId": 2,
                  "name": "Producto actualizado",
                  "description": "Descripcion actualizada",
                  "price": 4500,
                  "image": "%s",
                  "available": true
                }
                """.formatted(IMAGE_KEY);

        mockMvc.perform(put("/api/catalog/my/products/1")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.name").value("Producto actualizado"));
    }

    @Test
    void updateAndDeleteRejectProductsFromAnotherRestaurant() throws Exception {
        createInternalProductForRestaurantTwo();
        String body = """
                {
                  "categoryId": 1,
                  "name": "Intento IDOR",
                  "price": 4500,
                  "image": "%s",
                  "available": true
                }
                """.formatted(IMAGE_KEY);

        mockMvc.perform(put("/api/catalog/my/products/3")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/catalog/my/products/3").with(ownerJwt(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void deleteSoftDeletesAndPublicCatalogHidesProduct() throws Exception {
        mockMvc.perform(delete("/api/catalog/my/products/1").with(ownerJwt(1L)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/catalog/my/products").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].available").value(false));

        mockMvc.perform(get("/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(1))));
    }

    @Test
    void userWithoutRestaurantGetsForbidden() throws Exception {
        mockMvc.perform(get("/api/catalog/my/products").with(ownerJwt(999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("No existe un restaurante asociado al usuario autenticado"));
    }

    private RequestPostProcessor ownerJwt(Long userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_Restaurante"));
    }

    private String createBody(String name, boolean available, Long restaurantId) {
        String ignoredRestaurant = restaurantId == null ? "" : "\"restaurantId\":" + restaurantId + ",";
        return """
                {
                  %s
                  "categoryId": 1,
                  "name": "%s",
                  "description": "Descripcion",
                  "price": 3500,
                  "image": "%s",
                  "available": %s
                }
                """.formatted(ignoredRestaurant, name, IMAGE_KEY, available);
    }

    private void createInternalProductForRestaurantTwo() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 2,
                  "name": "Producto de otra tienda",
                  "price": 3500,
                  "available": true
                }
                """;

        mockMvc.perform(post("/api/internal/products")
                        .header("X-Internal-Api-Key", "dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
