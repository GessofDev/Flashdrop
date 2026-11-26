package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MyStoreMerchantControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantMemberRepositoryPort merchantMemberRepository;

    @Autowired
    private RestaurantRepositoryPort restaurantRepository;

    @Test
    void getMerchant_returnsBrandAndRoleForOwner() throws Exception {
        mockMvc.perform(get("/api/catalog/my/merchant").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Flash Restaurant Demo"))
                .andExpect(jsonPath("$.role").value("DUEÑO"));
    }

    @Test
    void listRestaurants_returnsOperableStoresWithMerchantName() throws Exception {
        mockMvc.perform(get("/api/catalog/my/restaurants").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].merchantName").value("Flash Restaurant Demo"));
    }

    @Test
    void createRestaurant_asDueno_createsClosedStoreAndEnforcesContextHeader() throws Exception {
        mockMvc.perform(post("/api/catalog/my/restaurants")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sucursal Centro\",\"address\":\"Alameda 500\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sucursal Centro"))
                .andExpect(jsonPath("$.merchantName").value("Flash Restaurant Demo"))
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(get("/api/catalog/my/restaurants").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Without header and >1 stores -> 400 LOCAL_CONTEXT_REQUIRED with availableRestaurants
        mockMvc.perform(get("/api/catalog/my/restaurant").with(ownerJwt(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LOCAL_CONTEXT_REQUIRED"))
                .andExpect(jsonPath("$.availableRestaurants", hasSize(2)));

        // With header selecting the new store -> 200 OK
        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Sucursal Centro"))
                .andExpect(jsonPath("$.merchantName").value("Flash Restaurant Demo"));
    }

    @Test
    void createRestaurant_asEncargado_returns403() throws Exception {
        merchantMemberRepository.addMember(1L, 50L, MerchantRole.ENCARGADO);

        mockMvc.perform(post("/api/catalog/my/restaurants")
                        .with(ownerJwt(50L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sucursal Norte\",\"address\":\"Calle 1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRestaurant_userWithoutMerchant_autoCreatesMerchantAndCreatesStore() throws Exception {
        mockMvc.perform(post("/api/catalog/my/restaurants")
                        .with(ownerJwt(77L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nueva Marca Sucursal 1\",\"address\":\"Calle Nueva 20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantName").value("Nueva Marca Sucursal 1"))
                .andExpect(jsonPath("$.acceptingOrders").value(false));

        assertEquals(1, merchantMemberRepository.findByUserId(77L).size());
        Long merchantId = merchantMemberRepository.findByUserId(77L).get(0).getMerchantId();
        assertNotNull(merchantId);
        assertEquals(1, restaurantRepository.findByMerchantIds(java.util.List.of(merchantId)).size());
    }

    @Test
    void getMyRestaurant_headerValidation_handlesForeignMissingAndInvalid() throws Exception {
        // Foreign store (store 2 belongs to user 2) -> 403
        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "2"))
                .andExpect(status().isForbidden());

        // Nonexistent store -> 404
        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "9999"))
                .andExpect(status().isNotFound());

        // Invalid header -> 400
        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicListRestaurants_includesMerchantName() throws Exception {
        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantName").value("Flash Restaurant Demo"));
    }

    private RequestPostProcessor ownerJwt(Long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_Restaurante"));
    }
}
