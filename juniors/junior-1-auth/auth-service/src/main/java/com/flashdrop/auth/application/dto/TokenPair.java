package com.flashdrop.auth.application.dto;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
