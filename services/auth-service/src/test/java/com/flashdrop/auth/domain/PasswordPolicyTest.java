package com.flashdrop.auth.domain;

import com.flashdrop.auth.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void aceptaContrasenaFuerte() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Segura1234"));
    }

    @Test
    void rechazaCorta() {
        assertThrows(WeakPasswordException.class, () -> PasswordPolicy.validate("Ab1"));
    }

    @Test
    void rechazaSinNumeroNiMayuscula() {
        assertThrows(WeakPasswordException.class, () -> PasswordPolicy.validate("solominusculas"));
    }
}
