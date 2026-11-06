package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.CreateDeliveryPersonUseCase;
import com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.config.SecurityConfig;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import com.flashdrop.delivery.infrastructure.security.JwtAuthenticationFilter;

import jakarta.servlet.FilterChain;

/** F6: alta de repartidores por el administrador (usuario con los tres roles). */
@WebMvcTest(controllers = AdminDeliveryPersonController.class)
@Import({SecurityConfig.class, CurrentUserResolver.class})
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://auth-service:8081/auth/.well-known/jwks.json"
})
class AdminDeliveryPersonControllerTest {

    private static final String BODY = "{\"userId\":77,\"vehicle\":\"MOTO\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @MockBean
    private CreateDeliveryPersonUseCase createDeliveryPersonUseCase;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(inv -> {
            jakarta.servlet.ServletRequest req = inv.getArgument(0);
            jakarta.servlet.ServletResponse res = inv.getArgument(1);
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        var authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return authentication(new UsernamePasswordAuthenticationToken(subject, null, authorities));
    }

    private static RequestPostProcessor admin() {
        return user("4", "Cliente", "Restaurante", "Repartidor");
    }

    @Test
    void withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/delivery/admin/persons").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdmins_areForbidden_evenWithTheCourierRole() throws Exception {
        RequestPostProcessor[] nonAdmins = {
                user("3", "Repartidor"),
                user("1", "Cliente"),
                user("2", "Restaurante"),
                user("6", "Restaurante", "Repartidor"),
                user("8", "Soporte")};
        for (RequestPostProcessor nonAdmin : nonAdmins) {
            mockMvc.perform(post("/api/delivery/admin/persons").with(nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verify(createDeliveryPersonUseCase, never()).execute(any(), any());
    }

    @Test
    void admin_createsTheProfileForTheGivenUser() throws Exception {
        when(createDeliveryPersonUseCase.execute(eq(77L), any(CreateDeliveryPersonRequest.class)))
                .thenReturn(new DeliveryPersonResponse(5L, "77", "MOTO", Instant.now()));

        mockMvc.perform(post("/api/delivery/admin/persons").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value("77"));

        verify(createDeliveryPersonUseCase).execute(eq(77L), eq(new CreateDeliveryPersonRequest(VehicleType.MOTO)));
    }

    @Test
    void admin_withInvalidBody_isBadRequest() throws Exception {
        String[] bodies = {"{}", "{\"userId\":0}", "{\"userId\":-1}"};
        for (String body : bodies) {
            mockMvc.perform(post("/api/delivery/admin/persons").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void admin_whenTheProfileAlreadyExists_isConflict() throws Exception {
        when(createDeliveryPersonUseCase.execute(eq(77L), any()))
                .thenThrow(new DeliveryPersonAlreadyExistsException("77"));

        mockMvc.perform(post("/api/delivery/admin/persons").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }
}
