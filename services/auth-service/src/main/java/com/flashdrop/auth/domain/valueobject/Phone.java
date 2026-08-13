package com.flashdrop.auth.domain.valueobject;

import com.flashdrop.auth.domain.exception.InvalidUserException;

import java.util.regex.Pattern;

/**
 * Teléfono en formato E.164 flexible (opcional +, 8 a 15 dígitos).
 */
public record Phone(String value) {

    private static final Pattern PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");

    public Phone {
        String normalized = value == null ? null : value.replaceAll("\\s+", "");
        if (normalized == null || !PATTERN.matcher(normalized).matches()) {
            throw new InvalidUserException("Teléfono inválido");
        }
        value = normalized;
    }
}
