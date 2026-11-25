package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;

/** FR-15: account deletion removes merchant_members rows while preserving the merchant and its stores. */
@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternalErasureControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantMemberRepositoryPort merchantMemberRepository;

    @Autowired
    private MerchantRepositoryPort merchantRepository;

    @Test
    void withoutTheInternalKey_isRejected() throws Exception {
        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void erasingTheOwner_removesMerchantMembersAndPreservesMerchantAndStores_andIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(true));

        assertThat(merchantMemberRepository.findByUserId(1L)).isEmpty();
        assertThat(merchantRepository.findById(1L)).isPresent();

        mockMvc.perform(get("/api/internal/restaurants/1").header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(get("/api/internal/restaurants").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/internal/owner/restaurants").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/internal/erasure/restaurant-owner").param("userId", "1")
                        .header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(false));
    }
}
