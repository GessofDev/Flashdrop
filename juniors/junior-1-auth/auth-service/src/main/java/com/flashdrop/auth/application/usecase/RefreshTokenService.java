package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.RefreshCommand;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.dto.TokenPair;
import com.flashdrop.auth.application.port.inbound.LogoutUseCase;
import com.flashdrop.auth.application.port.inbound.RefreshTokenUseCase;
import com.flashdrop.auth.application.port.outbound.TokenService;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.model.User;

public class RefreshTokenService implements RefreshTokenUseCase, LogoutUseCase {

    private final RefreshTokenManager refreshTokens;
    private final UserRepository users;
    private final TokenService tokens;

    public RefreshTokenService(RefreshTokenManager refreshTokens, UserRepository users, TokenService tokens) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.tokens = tokens;
    }

    @Override
    public TokenPair refresh(RefreshCommand command) {
        var rotation = refreshTokens.rotate(command.refreshToken());
        User user = users.findById(rotation.userId()).orElseThrow(InvalidTokenException::new);
        String access = tokens.issue(new TokenClaims(user.id(), user.email().value(), user.roleNames()));
        return new TokenPair(access, rotation.newRefreshToken(), tokens.accessTtlSeconds());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }
}
