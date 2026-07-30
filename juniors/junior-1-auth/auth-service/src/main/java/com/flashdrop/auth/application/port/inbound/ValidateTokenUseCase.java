package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.TokenClaims;

public interface ValidateTokenUseCase {
    TokenClaims validate(String token);
}
