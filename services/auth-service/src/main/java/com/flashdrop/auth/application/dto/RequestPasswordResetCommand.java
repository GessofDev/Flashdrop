package com.flashdrop.auth.application.dto;

public record RequestPasswordResetCommand(String email, String clientIp) {
}
