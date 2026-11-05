package com.flashdrop.auth.application.dto;

public record ResetPasswordCommand(String token, String newPassword, String clientIp) {
}
