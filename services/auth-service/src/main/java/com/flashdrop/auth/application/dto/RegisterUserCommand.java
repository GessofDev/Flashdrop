package com.flashdrop.auth.application.dto;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String rut,
        String name,
        String lastName,
        String phone
) {
}
