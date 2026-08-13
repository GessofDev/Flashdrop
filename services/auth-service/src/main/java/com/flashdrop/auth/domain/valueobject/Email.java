package com.flashdrop.auth.domain.valueobject;

import com.flashdrop.auth.domain.exception.InvalidEmailException;

import java.util.regex.Pattern;

/**
 * Value object inmutable: un Email siempre es válido o no existe.
 * La validación vive en el dominio, no en la capa web.
 */
public record Email(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        String normalized = value == null ? null : value.trim().toLowerCase();
        if (normalized == null || !PATTERN.matcher(normalized).matches()) {
            throw new InvalidEmailException(value);
        }
        value = normalized;
    }
}
