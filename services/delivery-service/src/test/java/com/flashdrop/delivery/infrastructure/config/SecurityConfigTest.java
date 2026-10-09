package com.flashdrop.delivery.infrastructure.config;

import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.adapter.inbound.rest.DeliveryController;
import com.flashdrop.delivery.infrastructure.adapter.inbound.rest.RouteController;
import com.flashdrop.delivery.infrastructure.security.JwtAuthenticationFilter;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link SecurityConfig} enforces the REPARTIDOR role on the
 * delivery-service endpoints and lets actuator/health through.
 *
 * <p>{@link JwtAuthenticationFilter} is mocked here — its real behaviour is
 * covered by {@link JwtAuthenticationFilterTest}. We mock the filter to pass
 * requests through the chain so this test only exercises the matcher map
 * itself. The roles that the real filter would extract from the JWT are
 * injected directly via {@code .with(authentication(...))}.
 *
 * <p>WU-3 of the openspec-feedback fix for delivery-service. Before WU-3 the
 * matcher was just {@code .authenticated()}, which meant any caller with a
 * valid JWT (Cliente, Restaurante or Repartidor) could reach the courier
 * endpoints. After WU-3 only callers whose JWT carries
 * {@code roles: ["Repartidor"]} (mapped to {@code ROLE_Repartidor} by the
 * filter) pass the matcher.
 */
@WebMvcTest(controllers = {RouteController.class, DeliveryController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://auth-service:8081/auth/.well-known/jwks.json"
})
@DisplayName("SecurityConfigTest — matcher map enforces REPARTIDOR role")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @MockBean
    private ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;

    @MockBean
    private UpdateRouteStatusUseCase updateRouteStatusUseCase;

    @MockBean
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    /**
     * RouteController resolves deliveryPersonId by calling
     * {@code findByUserId} on this repo (PR-A: IDOR fix / list-routes filter).
     * WebMvcTest does not load @Repository adapters, so we mock the port here.
     */
    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Mock the JWT filter to behave like a pass-through so the matcher map
        // is what these tests assert on. The real filter is tested separately.
        doAnswer(inv -> {
            jakarta.servlet.ServletRequest req = inv.getArgument(0);
            jakarta.servlet.ServletResponse res = inv.getArgument(1);
            FilterChain chain = inv.getArgument(2);
            try {
                chain.doFilter(req, res);
            } catch (java.io.IOException | jakarta.servlet.ServletException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------------------
    // Without auth → 401
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("Without authentication")
    class WithoutAuth {

        @Test
        @DisplayName("TC1: GET /api/delivery/routes without JWT → 401")
        void getRoutes_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/delivery/routes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC2: GET /delivery/routes without JWT → 401")
        void getRoutesUnprefixed_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/delivery/routes"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------------
    // With auth + role → 200
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("With valid authentication and REPARTIDOR role")
    class WithAuthAndRepartidor {

        @Test
        @DisplayName("TC3: GET /api/delivery/routes with auth + ROLE_Repartidor → 200")
        void getRoutes_withRepartidorRole_returns200() throws Exception {
            // RouteController resolves deliveryPersonId via findByUserId before
            // delegating to the use case. Stub it so the controller reaches the
            // mocked ListDeliveryRoutesUseCase (returns 200 with empty list).
            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(Optional.of(new DeliveryPerson(
                            5L, "42", VehicleType.MOTO, Instant.now())));
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor"))))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC3b: GET /delivery/routes (unprefixed alias) with ROLE_Repartidor → 200")
        void getRoutesUnprefixed_withRepartidorRole_returns200() throws Exception {
            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(Optional.of(new DeliveryPerson(
                            5L, "42", VehicleType.MOTO, Instant.now())));
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor"))))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC3c: POST /api/delivery/claim with ROLE_Repartidor → reaches controller (201)")
        void claimDelivery_withRepartidorRole_returns201() throws Exception {
            when(claimDeliveryOrdersUseCase.execute(anyLong(), any(com.flashdrop.delivery.application.dto.ClaimDeliveryRequest.class)))
                    .thenReturn(List.of());

            mockMvc.perform(post("/api/delivery/claim")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderIds\":[101]}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("TC3d: multi-role token (Cliente + Repartidor) — the REPARTIDOR role still matches")
        void multiRoleTokenWithRepartidor_returns200() throws Exception {
            when(deliveryPersonRepository.findByUserId("42"))
                    .thenReturn(Optional.of(new DeliveryPerson(
                            5L, "42", VehicleType.MOTO, Instant.now())));
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_Cliente"),
                                            new SimpleGrantedAuthority("ROLE_Repartidor"))))))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------------------------
    // With auth but wrong/missing role → 403
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("With authentication but wrong/missing role")
    class WithAuthButWrongRole {

        @Test
        @DisplayName("TC5: GET /api/delivery/routes with ROLE_Cliente (no Repartidor) → 403")
        void getRoutes_withClienteRole_returns403() throws Exception {
            mockMvc.perform(get("/api/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Cliente"))))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC5b: GET /delivery/routes with ROLE_Restaurante → 403")
        void getRoutes_withRestauranteRole_returns403() throws Exception {
            mockMvc.perform(get("/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Restaurante"))))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC5c: POST /api/delivery/claim with ROLE_Cliente → 403 (claim is for couriers, not clients)")
        void claimDelivery_withClienteRole_returns403() throws Exception {
            mockMvc.perform(post("/api/delivery/claim")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Cliente")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderIds\":[101]}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC6: GET /api/delivery/routes with empty authorities (token had no roles claim) → 403")
        void getRoutes_withEmptyAuthorities_returns403() throws Exception {
            // Fail-closed: a token without a 'roles' claim yields an empty
            // authority set, which does not match hasRole("REPARTIDOR") and
            // gets 403. This is the whole point of WU-3.
            mockMvc.perform(get("/api/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null, List.of()))))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------------------------
    // Public endpoints → 200 without auth
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("TC4: GET /actuator/health without JWT — security does NOT 401 it")
        void health_withoutAuth_securityDoesNotReject() throws Exception {
            // The point of this test is "the security chain did not 401 it" — we
            // hit /actuator/health which is permitAll(). @WebMvcTest doesn't
            // register Actuator handlers, so the dispatcher returns 404. We
            // assert NOT 401, which proves the matcher map honoured permitAll.
            int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(401);
        }
    }
}