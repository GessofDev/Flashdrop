package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.domain.exception.InvalidTokenException;

/**
 * Rechaza los tokens de cuentas inhabilitadas o eliminadas aunque la firma siga vigente. Con esto, Orders
 * (que valida cada request contra /auth/validate) deja de aceptar al instante la sesion de una cuenta que
 * pidio eliminarse. Decora a {@link ValidateTokenService}, que sigue siendo un delegador delgado.
 */
public class ActiveAccountTokenValidator implements ValidateTokenUseCase {

    private final ValidateTokenUseCase delegate;
    private final CredentialStore credentials;

    public ActiveAccountTokenValidator(ValidateTokenUseCase delegate, CredentialStore credentials) {
        this.delegate = delegate;
        this.credentials = credentials;
    }

    @Override
    public TokenClaims validate(String token) {
        TokenClaims claims = delegate.validate(token);
        if (!credentials.isActive(claims.userId())) {
            throw new InvalidTokenException();
        }
        return claims;
    }
}
