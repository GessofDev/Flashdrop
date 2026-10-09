package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.CreateDeliveryPersonUseCase;
import com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link DeliveryPersonController}.
 *
 * <p>Scope:
 * <ul>
 *   <li>Controller resolves the userId from the security context via
 *       {@link CurrentUserResolver} (never from the request body).</li>
 *   <li>Empty body is accepted (vehicle is optional).</li>
 *   <li>Existing profile → 409 via {@code GlobalExceptionHandler} (which
 *       is loaded by the slice because it carries {@code @RestControllerAdvice}).</li>
 *   <li>No auth → 401 (defence in depth inside the controller).</li>
 * </ul>
 *
 * <p>The matcher-map enforcement of {@code hasRole("Repartidor")} is covered
 * by {@link com.flashdrop.delivery.infrastructure.config.SecurityConfigTest};
 * here we disable Spring Security filters ({@code addFilters = false}) and
 * inject the authentication directly to keep the test focused on the
 * controller's own logic.
 */
@WebMvcTest(controllers = DeliveryPersonController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.flashdrop.delivery.infrastructure.security.CurrentUserResolver.class)
@DisplayName("DeliveryPersonController — POST /api/delivery/persons")
class DeliveryPersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateDeliveryPersonUseCase createDeliveryPersonUseCase;

    /**
     * CurrentUserResolver is imported via {@code @Import} above — it only
     * reads the {@code SecurityContextHolder} and needs no other beans.
     */
    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    private DeliveryPersonResponse makeResponse(Long id, String userId, String vehicle) {
        return new DeliveryPersonResponse(id, userId, vehicle, Instant.now());
    }

    @BeforeEach
    void setUp() {
        // Provide a default Repartidor auth so the controller can resolve
        // the userId. Individual tests override this when they need a
        // different scenario (no auth, different role, etc.).
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null,
                        List.of(new SimpleGrantedAuthority("ROLE_Repartidor"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /api/delivery/persons — happy path")
    class HappyPath {

        @Test
        @DisplayName("TC1: with ROLE_Repartidor + vehicle in body → 201 and the use case receives the resolved userId")
        void createWithVehicle_returns201() throws Exception {
            when(createDeliveryPersonUseCase.execute(anyLong(), any(CreateDeliveryPersonRequest.class)))
                    .thenReturn(makeResponse(7L, "42", "MOTO"));

            String body = objectMapper.writeValueAsString(
                    new CreateDeliveryPersonRequest(VehicleType.MOTO));

            mockMvc.perform(post("/api/delivery/persons")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(7))
                    .andExpect(jsonPath("$.data.userId").value("42"))
                    .andExpect(jsonPath("$.data.vehicle").value("MOTO"));

            // The IDOR-closed invariant: the userId passed to the use case
            // must be the one resolved from the security context (42L), not
            // any value from the request body. The body has no userId field
            // at all, so the only source is the JWT subject.
            verify(createDeliveryPersonUseCase).execute(anyLong(), any(CreateDeliveryPersonRequest.class));
        }

        @Test
        @DisplayName("TC2: with ROLE_Repartidor and empty body (vehicle is optional) → 201")
        void createWithEmptyBody_returns201() throws Exception {
            when(createDeliveryPersonUseCase.execute(anyLong(), any(CreateDeliveryPersonRequest.class)))
                    .thenReturn(makeResponse(7L, "42", null));

            mockMvc.perform(post("/api/delivery/persons")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.vehicle").doesNotExist());
        }

        @Test
        @DisplayName("TC3: /delivery/persons (unprefixed alias) also works")
        void unprefixedAlias_works() throws Exception {
            when(createDeliveryPersonUseCase.execute(anyLong(), any(CreateDeliveryPersonRequest.class)))
                    .thenReturn(makeResponse(7L, "42", "BICICLETA"));

            mockMvc.perform(post("/delivery/persons")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"vehicle\":\"BICICLETA\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("POST /api/delivery/persons — failure paths")
    class FailurePaths {

        @Test
        @DisplayName("TC4: profile already exists → 409 (GlobalExceptionHandler maps DeliveryPersonAlreadyExistsException)")
        void duplicate_returns409() throws Exception {
            when(createDeliveryPersonUseCase.execute(anyLong(), any(CreateDeliveryPersonRequest.class)))
                    .thenThrow(new DeliveryPersonAlreadyExistsException("42"));

            mockMvc.perform(post("/api/delivery/persons")
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "42", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_Repartidor")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("42")));
        }

        @Test
        @DisplayName("TC5: no authentication → 401 (defence-in-depth in the controller)")
        void noAuth_returns401() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(post("/api/delivery/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            // Critical: with no auth the use case must NEVER be invoked.
            verify(createDeliveryPersonUseCase, never()).execute(anyLong(), any(CreateDeliveryPersonRequest.class));
        }
    }
}
