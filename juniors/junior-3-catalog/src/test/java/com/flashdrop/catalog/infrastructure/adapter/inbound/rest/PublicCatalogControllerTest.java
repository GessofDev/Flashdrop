package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PublicCatalogControllerTest {

    private static final String INTERNAL_API_KEY = "dev-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listCategoriesReturnsPublicContractFields() throws Exception {
        mockMvc.perform(get("/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Bebidas"))
                .andExpect(jsonPath("$[0].description").value("Bebidas frias"))
                .andExpect(jsonPath("$[0].image").value("assets/img/bag.png"));
    }

    @Test
    void listRestaurantsReturnsPublicContractFields() throws Exception {
        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Flash Restaurant Demo"))
                .andExpect(jsonPath("$[0].address").value("Av. Providencia 1200, Santiago"));
    }

    @Test
    void listProductsReturnsPublicContractFields() throws Exception {
        mockMvc.perform(get("/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].restaurantId").value(1))
                .andExpect(jsonPath("$[0].name").value("Burger doble"))
                .andExpect(jsonPath("$[0].price").value(8990))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void createProductReturnsCreatedProduct() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 1,
                  "name": "Completo italiano",
                  "description": "Vienesa, tomate, palta y mayo",
                  "price": 3990,
                  "image": "assets/img/completo.png",
                  "available": true
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.name").value("Completo italiano"))
                .andExpect(jsonPath("$.description").value("Vienesa, tomate, palta y mayo"))
                .andExpect(jsonPath("$.price").value(3990))
                .andExpect(jsonPath("$.image").value("assets/img/completo.png"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void createProductRequiresApiKey() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 1,
                  "name": "Completo italiano",
                  "price": 3990
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Invalid internal API key"));
    }

    @Test
    void createProductReturnsBadRequestForNegativePrice() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 1,
                  "name": "Completo italiano",
                  "price": -100
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void createProductReturnsBadRequestForEmptyName() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 1,
                  "name": "",
                  "price": 3990
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void createProductReturnsNotFoundForMissingCategory() throws Exception {
        String body = """
                {
                  "categoryId": 999,
                  "restaurantId": 1,
                  "name": "Completo italiano",
                  "price": 3990
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Category not found with id: 999"));
    }

    @Test
    void createProductReturnsNotFoundForMissingRestaurant() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 999,
                  "name": "Completo italiano",
                  "price": 3990
                }
                """;

        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Restaurant not found with id: 999"));
    }

    @Test
    void createProductReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/catalog/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es valido"));
    }

    @Test
    void validateProductsReturnsFoundProductsAndMissingIds() throws Exception {
        String body = """
                {
                  "productIds": [1, 999]
                }
                """;

        mockMvc.perform(post("/catalog/products/validate")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].id").value(1))
                .andExpect(jsonPath("$.missingIds", hasSize(1)))
                .andExpect(jsonPath("$.missingIds[0]").value(999));
    }

    @Test
    void validateProductsRequiresApiKey() throws Exception {
        String body = """
                {
                  "productIds": [1, 999]
                }
                """;

        mockMvc.perform(post("/catalog/products/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Invalid internal API key"));
    }
}
