package com.flashdrop.auth.application.dto;

public record RequestEmailChangeCommand(Long userId, String password, String clientIp) {
}
