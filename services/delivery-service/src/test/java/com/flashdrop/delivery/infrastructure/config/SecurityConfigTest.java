package com.flashdrop.delivery.infrastructure.config;

import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link SecurityConfig} enforces authentication on the
 * delivery-service endpoints and lets actuator/health through.
 *
 * <p>{@link JwtAuthenticationFilter} is mocked here — its real behaviour is
 * covered by {@link JwtAuthenticationFilterTest}. We mock the filter to pass
 * requests through the chain so this test only exercises the matcher map
 * itself.
 */
@WebMvcTest(controllers = {RouteController.class, DeliveryController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.issuer=flashdrop-auth",
        "auth.jwks-uri=http://auth-service:8081/auth/.well-known/jwks.json"
})
@DisplayName("SecurityConfigTest — PR-A: matcher map enforces JWT auth")
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
    // With auth → 200
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("With valid authentication")
    class WithAuth {

        @Test
        @DisplayName("TC3: GET /api/delivery/routes with auth → 200")
        void getRoutes_withAuth_returns200() throws Exception {
            when(listDeliveryRoutesUseCase.execute(any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/delivery/routes")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null, List.of()))))
                    .andExpect(status().isOk());
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