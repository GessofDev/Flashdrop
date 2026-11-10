package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.auth.application.dto.RequestAccountDeletionCommand;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.RequestAccountDeletionUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.exception.AccountDeletionInProgressException;
import com.flashdrop.auth.domain.exception.AccountErasureUnavailableException;
import com.flashdrop.auth.domain.exception.InvalidCredentialsException;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;

class AccountControllerTest {

    private final ValidateTokenUseCase validate = mock(ValidateTokenUseCase.class);
    private final RequestAccountDeletionUseCase deletion = mock(RequestAccountDeletionUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new AccountController(validate, deletion))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(validate.validate("tok")).thenReturn(new TokenClaims(42L, "ana@correo.cl", List.of("Cliente")));
    }

    private MockMvc perform() {
        return mvc;
    }

    @Test
    void delete_withTheRightPassword_isAccepted_andUsesTheIdentityOfTheToken() throws Exception {
        perform().perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Segura1234\"}"))
                .andExpect(status().isAccepted());

        verify(deletion).request(new RequestAccountDeletionCommand(42L, "Segura1234", "127.0.0.1"));
    }

    @Test
    void delete_withoutBearerToken_isUnauthorized() throws Exception {
        mvc.perform(delete("/auth/account").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deletion);
    }

    @Test
    void delete_withAnInvalidToken_isUnauthorized() throws Exception {
        when(validate.validate("malo")).thenThrow(new InvalidTokenException());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer malo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deletion);
    }

    @Test
    void delete_withoutPassword_isBadRequest() throws Exception {
        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deletion);
    }

    @Test
    void delete_withAWrongPassword_isUnauthorized() throws Exception {
        doThrow(new InvalidCredentialsException()).when(deletion).request(any());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_withActiveOrders_isConflictWithAClearMessage() throws Exception {
        doThrow(new AccountDeletionBlockedException("No puedes eliminar tu cuenta mientras tengas pedidos en curso"))
                .when(deletion).request(any());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Segura1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_ACTIVE_PROCESSES"));
    }

    @Test
    void delete_whenAlreadyInProgress_isConflict() throws Exception {
        doThrow(new AccountDeletionInProgressException()).when(deletion).request(any());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Segura1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_IN_PROGRESS"));
    }

    @Test
    void delete_whenOrdersIsDown_isServiceUnavailable() throws Exception {
        doThrow(new AccountErasureUnavailableException("Orders no disponible")).when(deletion).request(any());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Segura1234\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void delete_whenRateLimited_isTooManyRequests() throws Exception {
        doThrow(new RateLimitExceededException()).when(deletion).request(any());

        mvc.perform(delete("/auth/account").header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
