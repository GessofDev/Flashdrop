package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.application.port.outbound.TokenService;

public class ValidateTokenService implements ValidateTokenUseCase {

    private final TokenService tokens;

    public ValidateTokenService(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public TokenClaims validate(String token) {
        return tokens.verify(token);
    }
}
