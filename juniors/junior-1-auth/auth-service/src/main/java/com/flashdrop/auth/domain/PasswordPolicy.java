package com.flashdrop.auth.domain;

import com.flashdrop.auth.domain.exception.WeakPasswordException;

/**
 * Política de contraseñas (S-13). Lógica de dominio pura.
 * Mínimo 10 caracteres con mayúscula, minúscula y dígito.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void validate(String raw) {
        if (raw == null || raw.length() < 10) {
            throw new WeakPasswordException("La contraseña debe tener al menos 10 caracteres");
        }
        if (!raw.matches(".*[A-Z].*") || !raw.matches(".*[a-z].*") || !raw.matches(".*[0-9].*")) {
            throw new WeakPasswordException("La contraseña debe incluir mayúscula, minúscula y número");
        }
    }
}
