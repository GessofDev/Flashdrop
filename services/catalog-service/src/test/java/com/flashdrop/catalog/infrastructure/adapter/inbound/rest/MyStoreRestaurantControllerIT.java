package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneId;

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

@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MyStoreRestaurantControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Test
    void endpointsRequireTheRestaurantRole() throws Exception {
        mockMvc.perform(get("/api/catalog/my/restaurant"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerWithoutRestaurantReceives404() throws Exception {
        mockMvc.perform(get("/api/catalog/my/restaurant").with(ownerJwt(99L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void closingTheStoreIsVisibleToCustomersAndToOrders() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/open-status")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptingOrders\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.acceptingOrders").value(false));

        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 1)].open").value(false))
                .andExpect(jsonPath("$[?(@.id == 2)].open").value(true));

        mockMvc.perform(get("/api/internal/restaurants/1").header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    void hoursAreReplacedAndReadBackInLocalFormat() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/hours")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours":[{"day":1,"opensAt":"09:00","closesAt":"18:00"},
                                          {"day":5,"opensAt":"22:00","closesAt":"02:00"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours", hasSize(2)));

        mockMvc.perform(get("/api/catalog/my/restaurant").with(ownerJwt(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours[1].day").value(5))
                .andExpect(jsonPath("$.hours[1].opensAt").value("22:00"))
                .andExpect(jsonPath("$.hours[1].closesAt").value("02:00"));

        mockMvc.perform(put("/api/catalog/my/restaurant/hours")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours", hasSize(0)))
                .andExpect(jsonPath("$.open").value(true));
    }

    @Test
    void invalidHoursAreRejected() throws Exception {
        for (String body : new String[] {
                "{\"hours\":[{\"day\":8,\"opensAt\":\"09:00\",\"closesAt\":\"18:00\"}]}",
                "{\"hours\":[{\"day\":1,\"opensAt\":\"09:00\",\"closesAt\":\"09:00\"}]}",
                "{\"hours\":[{\"day\":1,\"opensAt\":\"25:00\",\"closesAt\":\"18:00\"}]}",
                "{\"hours\":[{\"day\":1,\"opensAt\":\"09:00\",\"closesAt\":\"12:00\"},"
                        + "{\"day\":1,\"opensAt\":\"15:00\",\"closesAt\":\"18:00\"}]}",
                "{}"
        }) {
            mockMvc.perform(put("/api/catalog/my/restaurant/hours")
                            .with(ownerJwt(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void storeTimeUsesTheChileanZoneByDefault() {
        assertEquals(ZoneId.of("America/Santiago"), clock.getZone());
    }

    private static RequestPostProcessor ownerJwt(Long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_Restaurante"));
    }
}
