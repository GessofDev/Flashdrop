package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class InternalCatalogControllerTest {

    private static final String INTERNAL_API_KEY = "dev-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productsByIdsReturnsContractFields() throws Exception {
        mockMvc.perform(get("/api/internal/products")
                        .param("ids", "1,2")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].restaurantId").value(1))
                .andExpect(jsonPath("$[0].name").value("Burger doble"))
                .andExpect(jsonPath("$[0].price").value(8990))
                .andExpect(jsonPath("$[0].isAvailable").value(true));
    }

    @Test
    void productsByIdsReturnsEmptyArrayWhenMissing() throws Exception {
        mockMvc.perform(get("/api/internal/products")
                        .param("ids", "999")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void restaurantByIdReturnsContractFields() throws Exception {
        mockMvc.perform(get("/api/internal/restaurants/1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Flash Restaurant Demo"))
                .andExpect(jsonPath("$.address").value("Av. Providencia 1200, Santiago"));
    }

    @Test
    void restaurantByUserIdReturnsContractFields() throws Exception {
        mockMvc.perform(get("/api/internal/restaurants")
                        .param("userId", "2")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.name").value("Urban Burger Demo"));
    }

    @Test
    void restaurantByIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/internal/restaurants/999")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Restaurant not found with id: 999"));
    }

    @Test
    void internalEndpointsRequireApiKey() throws Exception {
        mockMvc.perform(get("/api/internal/products").param("ids", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Invalid internal API key"));
    }
}
