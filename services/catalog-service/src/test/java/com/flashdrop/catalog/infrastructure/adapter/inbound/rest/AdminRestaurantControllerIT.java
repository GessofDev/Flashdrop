package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

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

/** F6: alta de tiendas por el administrador (usuario con los tres roles). */
@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminRestaurantControllerIT {

    private static final String NEW_STORE = "{\"name\":\"Nueva Tienda\",\"address\":\"Calle 1\",\"ownerUserId\":50}";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor user(long id, String... roles) {
        SimpleGrantedAuthority[] authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(token -> token.subject(String.valueOf(id))).authorities(authorities);
    }

    private static RequestPostProcessor admin() {
        return user(4L, "Cliente", "Restaurante", "Repartidor");
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/restaurants")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STORE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminUsers_areForbidden() throws Exception {
        RequestPostProcessor[] nonAdmins = {
                user(1L, "Cliente"),
                user(2L, "Restaurante"),
                user(3L, "Repartidor"),
                user(6L, "Cliente", "Restaurante"),
                user(7L, "Restaurante", "Repartidor"),
                user(8L, "Soporte")};
        for (RequestPostProcessor nonAdmin : nonAdmins) {
            mockMvc.perform(post("/api/catalog/admin/restaurants").with(nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content(NEW_STORE))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/catalog/admin/restaurants").with(nonAdmin))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void admin_createsAStoreThatAppearsInTheCatalogAndAdminList() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/restaurants").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STORE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nueva Tienda"))
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(get("/catalog/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Nueva Tienda")));
        mockMvc.perform(get("/api/catalog/admin/restaurants").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Nueva Tienda")));
    }

    @Test
    void newOwner_canManageTheStoreThroughMyRestaurant() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/restaurants").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_STORE))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/catalog/my/restaurant").with(user(50L, "Restaurante")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nueva Tienda"));
    }

    @Test
    void create_withInvalidBody_isBadRequest() throws Exception {
        String[] bodies = {"{}", "{\"name\":\"  \"}", "{\"name\":\"X\",\"ownerUserId\":0}"};
        for (String body : bodies) {
            mockMvc.perform(post("/api/catalog/admin/restaurants").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_withAnOwnerWhoAlreadyHasAStore_createsSecondStoreInSameMerchant() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/restaurants").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otra Sucursal\",\"ownerUserId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Otra Sucursal"));

        mockMvc.perform(get("/api/catalog/my/restaurants").with(user(1L, "Restaurante")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void assignOwner_toAnOwnerlessStore_worksForNewAndExistingOwners_andUnknownStoreIs404() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/restaurants").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sin dueno\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());

        mockMvc.perform(put("/api/catalog/admin/restaurants/3/owner").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));

        mockMvc.perform(get("/api/catalog/my/restaurants").with(user(60L, "Restaurante")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));

        mockMvc.perform(put("/api/catalog/admin/restaurants/999/owner").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":61}"))
                .andExpect(status().isNotFound());

        // Assigning an owner who already owns another store succeeds without OwnerAlreadyAssignedException
        mockMvc.perform(put("/api/catalog/admin/restaurants/3/owner").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }
}
