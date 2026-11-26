package com.flashdrop.auth.application.dto;

public record ConfirmEmailChangeCommand(String token, String newEmail, String clientIp) {
}
