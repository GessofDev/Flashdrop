package com.flashdrop.auth.application.dto;

import java.util.List;

/**
 * Claims que se extraen/inyectan en el JWT (S-14).
 * El ID se usa como 'sub'.
 */
public record TokenClaims(Long userId, String email, List<String> roles) {
}
