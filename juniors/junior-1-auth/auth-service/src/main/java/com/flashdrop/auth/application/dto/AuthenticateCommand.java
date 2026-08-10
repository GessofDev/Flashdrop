package com.flashdrop.auth.application.dto;

public record AuthenticateCommand(String login, String rawPassword, String clientIp) {
}
