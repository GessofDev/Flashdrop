package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.application.dto.TokenClaims;

public interface TokenService {
    String issue(TokenClaims claims);
    TokenClaims verify(String token);
    long accessTtlSeconds();
}
