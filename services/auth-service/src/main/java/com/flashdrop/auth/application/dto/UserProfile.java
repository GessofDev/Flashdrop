package com.flashdrop.auth.application.dto;

import java.util.List;

public record UserProfile(
        Long userId,
        String name,
        String lastName,
        String email,
        String phone,
        String rut,
        String photo,
        List<String> roles
) {
}
