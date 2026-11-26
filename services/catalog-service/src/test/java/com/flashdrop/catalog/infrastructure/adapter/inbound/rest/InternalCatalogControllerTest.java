package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternalCatalogControllerTest {

    private static final String INTERNAL_API_KEY = "dev-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepositoryPort restaurantRepository;

    @Autowired
    private MerchantMemberRepositoryPort merchantMemberRepository;

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
    void productsByIdsReturnsBadRequestWhenIdsIsMissing() throws Exception {
        mockMvc.perform(get("/api/internal/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProductReturnsCreatedInternalContractFields() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "restaurantId": 1,
                  "name": "Churrasco italiano",
                  "description": "Carne, tomate, palta y mayo",
                  "price": 6990,
                  "image": "assets/img/churrasco.png",
                  "available": true
                }
                """;

        mockMvc.perform(post("/api/internal/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.name").value("Churrasco italiano"))
                .andExpect(jsonPath("$.description").value("Carne, tomate, palta y mayo"))
                .andExpect(jsonPath("$.price").value(6990))
                .andExpect(jsonPath("$.image").value("assets/img/churrasco.png"))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void createProductReturnsNotFoundForMissingCategory() throws Exception {
        String body = """
                {
                  "categoryId": 999,
                  "restaurantId": 1,
                  "name": "Churrasco italiano",
                  "price": 6990
                }
                """;

        mockMvc.perform(post("/api/internal/products")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Category not found with id: 999"));
    }

    @Test
    void updateProductReturnsUpdatedInternalContractFields() throws Exception {
        String body = """
                {
                  "name": "Burger doble actualizada",
                  "price": 9990,
                  "available": false
                }
                """;

        mockMvc.perform(patch("/api/internal/products/1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.name").value("Burger doble actualizada"))
                .andExpect(jsonPath("$.description").value("Doble carne, queso y salsa de la casa"))
                .andExpect(jsonPath("$.price").value(9990))
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    void updateProductReturnsNotFound() throws Exception {
        String body = """
                {
                  "name": "Producto inexistente"
                }
                """;

        mockMvc.perform(patch("/api/internal/products/999")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999"));
    }

    @Test
    void updateProductReturnsBadRequestWhenBodyHasNoChanges() throws Exception {
        mockMvc.perform(patch("/api/internal/products/1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Debe enviar al menos un campo para actualizar"));
    }

    @Test
    void restaurantByIdReturnsContractFields() throws Exception {
        mockMvc.perform(get("/api/internal/restaurants/1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.merchantId").value(1))
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
                .andExpect(jsonPath("$.merchantId").value(2))
                .andExpect(jsonPath("$.name").value("Urban Burger Demo"));
    }

    @Test
    void restaurantByUserIdReturnsNotFoundWhenUserHasNoStore() throws Exception {
        mockMvc.perform(get("/api/internal/restaurants")
                        .param("userId", "999")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNotFound());
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
    void assignRestaurantOwnerChangesOwnerForTheCommerce() throws Exception {
        mockMvc.perform(put("/api/internal/restaurants/1/owner")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.merchantId").value(1))
                .andExpect(jsonPath("$.name").value("Flash Restaurant Demo"));

        mockMvc.perform(get("/api/internal/restaurants")
                        .param("userId", "42")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void assignRestaurantOwnerAllowsUserAlreadyLinkedToAnotherCommerce() throws Exception {
        mockMvc.perform(put("/api/internal/restaurants/1/owner")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.merchantId").value(1));

        mockMvc.perform(get("/api/internal/owner/restaurants/1/membership")
                        .param("userId", "2")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignRestaurantOwnerRequiresInternalKey() throws Exception {
        mockMvc.perform(put("/api/internal/restaurants/1/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEndpointsRequireApiKey() throws Exception {
        mockMvc.perform(get("/api/internal/products").param("ids", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Invalid internal API key"));

        mockMvc.perform(get("/api/internal/owner/restaurants").param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/internal/owner/restaurants/1/membership").param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void ownerRestaurantsReturnsAllOperableStoresWithMerchantId() throws Exception {
        Restaurant branch = restaurantRepository.createInMerchant(
                "Flash Sucursal Norte", "Av. Kennedy 300", 1L, true, null, null, null);

        mockMvc.perform(get("/api/internal/owner/restaurants")
                        .param("userId", "1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].merchantId").value(1))
                .andExpect(jsonPath("$[1].id").value(branch.getId()))
                .andExpect(jsonPath("$[1].merchantId").value(1))
                .andExpect(jsonPath("$[1].name").value("Flash Sucursal Norte"));
    }

    @Test
    void ownerRestaurantsReturnsEmptyListWhenUserHasNoOperableStores() throws Exception {
        mockMvc.perform(get("/api/internal/owner/restaurants")
                        .param("userId", "999")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void membershipReturnsNoContentForOperableStoreAndNotFoundOtherwise() throws Exception {
        Restaurant branch = restaurantRepository.createInMerchant(
                "Flash Sucursal Norte", "Av. Kennedy 300", 1L, true, null, null, null);
        merchantMemberRepository.addMember(1L, 50L, MerchantRole.ENCARGADO);

        // Owner operates both primary and branch store
        mockMvc.perform(get("/api/internal/owner/restaurants/1/membership")
                        .param("userId", "1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/internal/owner/restaurants/" + branch.getId() + "/membership")
                        .param("userId", "1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNoContent());

        // ENCARGADO operates merchant's stores
        mockMvc.perform(get("/api/internal/owner/restaurants/" + branch.getId() + "/membership")
                        .param("userId", "50")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNoContent());

        // Foreign store -> 404
        mockMvc.perform(get("/api/internal/owner/restaurants/2/membership")
                        .param("userId", "1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNotFound());

        // Missing store -> 404
        mockMvc.perform(get("/api/internal/owner/restaurants/999/membership")
                        .param("userId", "1")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(status().isNotFound());
    }
}
