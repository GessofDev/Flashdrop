package com.flashdrop.auth.application.dto;

public record RequestAccountDeletionCommand(Long userId, String password, String clientIp) {
}
