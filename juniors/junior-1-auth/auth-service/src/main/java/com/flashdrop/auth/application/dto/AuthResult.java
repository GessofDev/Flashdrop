package com.flashdrop.auth.application.dto;

import java.util.List;

public record AuthResult(
        Long userId,
        String name,
        String email,
        List<String> roles,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
