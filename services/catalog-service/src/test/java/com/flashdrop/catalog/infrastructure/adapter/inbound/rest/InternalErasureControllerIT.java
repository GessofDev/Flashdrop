package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** F5: el dueno elimina su cuenta; la tienda se conserva, cerrada y sin dueno. */
@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternalErasureControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withoutTheInternalKey_isRejected() throws Exception {
        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void erasingTheOwner_keepsTheRestaurantClosedAndOwnerless_andIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(true));

        mockMvc.perform(get("/api/internal/restaurants/1").header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
        mockMvc.perform(get("/api/internal/restaurants").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(false));
    }
}
