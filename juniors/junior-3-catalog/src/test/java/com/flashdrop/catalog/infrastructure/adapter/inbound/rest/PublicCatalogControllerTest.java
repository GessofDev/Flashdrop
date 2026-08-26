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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listCategoriesReturnsPublicContractFields() throws Exception {
        mockMvc.perform(get("/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Hamburguesas"))
                .andExpect(jsonPath("$[0].description").value("Sandwiches y burgers"))
                .andExpect(jsonPath("$[0].image").value("assets/img/burger1.png"));
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
    void validateProductsReturnsFoundProductsAndMissingIds() throws Exception {
        String body = """
                {
                  "productIds": [1, 999]
                }
                """;

        mockMvc.perform(post("/catalog/products/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].id").value(1))
                .andExpect(jsonPath("$.missingIds", hasSize(1)))
                .andExpect(jsonPath("$.missingIds[0]").value(999));
    }
}
