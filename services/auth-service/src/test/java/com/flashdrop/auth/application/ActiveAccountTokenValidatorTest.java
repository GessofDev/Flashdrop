package com.flashdrop.auth.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.usecase.ActiveAccountTokenValidator;
import com.flashdrop.auth.domain.exception.InvalidTokenException;

class ActiveAccountTokenValidatorTest {

    private final ValidateTokenUseCase delegate = mock(ValidateTokenUseCase.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final ActiveAccountTokenValidator validator = new ActiveAccountTokenValidator(delegate, credentials);
    private final TokenClaims claims = new TokenClaims(7L, "a@b.cl", List.of("Cliente"));

    @Test
    void validate_activeAccount_returnsTheSameClaims() {
        when(delegate.validate("t")).thenReturn(claims);
        when(credentials.isActive(7L)).thenReturn(true);

        assertSame(claims, validator.validate("t"));
    }

    @Test
    void validate_disabledOrDeletedAccount_isRejectedEvenWithAValidSignature() {
        when(delegate.validate("t")).thenReturn(claims);
        when(credentials.isActive(7L)).thenReturn(false);

        assertThrows(InvalidTokenException.class, () -> validator.validate("t"));
    }

    @Test
    void validate_invalidSignature_propagatesWithoutLookingUpTheAccount() {
        when(delegate.validate("malo")).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> validator.validate("malo"));
        org.mockito.Mockito.verifyNoInteractions(credentials);
    }
}
