package com.flashdrop.delivery.infrastructure.exception;

import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.infrastructure.adapter.inbound.rest.RouteController;
import com.flashdrop.delivery.infrastructure.security.JwksKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Spring 6.1+ {@link NoResourceFoundException} (thrown for unmapped
 * paths) is mapped to HTTP 404 by {@link GlobalExceptionHandler}, not swallowed
 * by the catch-all {@code @ExceptionHandler(Exception.class)} as a 500.
 *
 * <p>Before the fix, the catch-all mapped {@code NoResourceFoundException} to
 * {@code INTERNAL_SERVER_ERROR}, so QA observed bogus 500s on
 * {@code GET /no-such-path} even with a valid JWT.
 */
@WebMvcTest(controllers = RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;

    @MockBean
    private UpdateRouteStatusUseCase updateRouteStatusUseCase;

    @MockBean
    private DeliveryPersonRepository deliveryPersonRepository;

    @MockBean
    private JwksKeyProvider jwksKeyProvider;

    @Nested
    @DisplayName("NoResourceFoundException (unmapped paths)")
    class UnmappedPaths {

        @Test
        @DisplayName("TC1: GET on an unmapped sub-path under /delivery returns 404 with the standard error shape")
        void unmappedPath_returns404WithStandardShape() throws Exception {
            mockMvc.perform(get("/delivery/routes/{id}", "non-existent-id-xyz"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("TC2: GET on a totally bogus path returns 404, not 500")
        void bogusPath_returns404NotServerError() throws Exception {
            mockMvc.perform(get("/no-such-path"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));
        }
    }

    @Nested
    @DisplayName("Direct handler invocation")
    class DirectHandlerInvocation {

        @Test
        @DisplayName("handleNoResourceFound returns 404 with the standard error shape")
        void handlerReturns404WithStandardShape() {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();
            NoResourceFoundException ex = new NoResourceFoundException(
                    HttpMethod.GET, "/delivery/routes/non-existent-id-xyz");

            ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(404);
            assertThat(response.getBody().get("error")).isEqualTo("Not Found");
            assertThat((String) response.getBody().get("message"))
                    .contains("GET")
                    .contains("/delivery/routes/non-existent-id-xyz");
        }
    }
}
