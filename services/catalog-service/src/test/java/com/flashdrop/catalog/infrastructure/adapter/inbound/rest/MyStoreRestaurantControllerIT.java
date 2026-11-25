package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void radius_ownerCanConfigureAndFilterWithoutExposingOtherStores() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/location").with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-33.45,\"longitude\":-70.66}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius").with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryRadiusKm\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryRadiusKm").value(5));
        mockMvc.perform(get("/catalog/restaurants?latitude=-33.44&longitude=-70.66"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1));
        mockMvc.perform(get("/catalog/restaurants?latitude=-34&longitude=-70.66"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/internal/restaurants/1").header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryRadiusKm").value(5));
    }

    @Test
    void radius_invalidRequestsAndWrongRole_reject() throws Exception {
        for (String body : new String[]{"{}", "{\"deliveryRadiusKm\":0}", "{\"deliveryRadiusKm\":51}",
                "{\"deliveryRadiusKm\":5.123}"}) {
            mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius").with(ownerJwt(1L))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Cliente")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryRadiusKm\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/catalog/restaurants?latitude=-33.44"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/catalog/restaurants?latitude=91&longitude=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void radius_authenticatedOwner_cannotUpdateSomeoneElsesStore() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius").with(ownerJwt(99L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryRadiusKm\":10}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/catalog/my/restaurant").with(ownerJwt(1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryRadiusKm").value(nullValue()));
    }

    @Test
    void radius_missingLocation_rejectsUntilOwnerSetsPin() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius").with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryRadiusKm\":5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryRadiusKm\":5}"))
                .andExpect(status().isUnauthorized());
    }

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
    void locationIsUpdatedClearedAndVisibleInternally() throws Exception {
        mockMvc.perform(put("/api/catalog/my/restaurant/location")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-33.448900,\"longitude\":-70.669300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-33.448900))
                .andExpect(jsonPath("$.longitude").value(-70.669300));

        mockMvc.perform(get("/api/internal/restaurants/1").header("X-Internal-Api-Key", "dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-33.448900))
                .andExpect(jsonPath("$.longitude").value(-70.669300));

        mockMvc.perform(put("/api/catalog/my/restaurant/location")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":null,\"longitude\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.longitude").value(nullValue()));
    }

    @Test
    void invalidLocationIsRejected() throws Exception {
        for (String body : new String[] {
                "{\"latitude\":-33.448900,\"longitude\":null}",
                "{\"latitude\":null,\"longitude\":-70.669300}",
                "{\"latitude\":91.0,\"longitude\":-70.669300}",
                "{\"latitude\":-33.448900,\"longitude\":181.0}"
        }) {
            mockMvc.perform(put("/api/catalog/my/restaurant/location")
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

    @Test
    void multiStoreOwner_putEndpointsOperateOnlyOnSelectedHeaderStore() throws Exception {
        // User 1 creates a second store (id 3, starts closed)
        mockMvc.perform(post("/api/catalog/my/restaurants")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sucursal Norte\",\"address\":\"Norte 123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));

        // Without header -> 400 LOCAL_CONTEXT_REQUIRED
        mockMvc.perform(put("/api/catalog/my/restaurant/open-status")
                        .with(ownerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptingOrders\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LOCAL_CONTEXT_REQUIRED"));

        // Foreign store header -> 403 FORBIDDEN
        mockMvc.perform(put("/api/catalog/my/restaurant/open-status")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptingOrders\":false}"))
                .andExpect(status().isForbidden());

        // Update open-status, hours, location, and delivery-radius on store 3 only
        mockMvc.perform(put("/api/catalog/my/restaurant/open-status")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptingOrders\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.acceptingOrders").value(true));

        mockMvc.perform(put("/api/catalog/my/restaurant/hours")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":[{\"day\":1,\"opensAt\":\"10:00\",\"closesAt\":\"20:00\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.hours", hasSize(1)));

        mockMvc.perform(put("/api/catalog/my/restaurant/location")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-33.40,\"longitude\":-70.60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.latitude").value(-33.40));

        mockMvc.perform(put("/api/catalog/my/restaurant/delivery-radius")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryRadiusKm\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.deliveryRadiusKm").value(8));

        // Verify store 1 was untouched
        mockMvc.perform(get("/api/catalog/my/restaurant")
                        .with(ownerJwt(1L))
                        .header("X-My-Store-Restaurant", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.hours", hasSize(0)))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.deliveryRadiusKm").value(nullValue()));
    }

    private static RequestPostProcessor ownerJwt(Long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_Restaurante"));
    }
}
