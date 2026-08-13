package com.flashdrop.auth.domain;

import com.flashdrop.auth.domain.exception.InvalidEmailException;
import com.flashdrop.auth.domain.valueobject.Email;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void normalizaAMinusculasYQuitaEspacios() {
        assertEquals("user@flashdrop.cl", new Email("  User@FlashDrop.CL ").value());
    }

    @Test
    void rechazaEmailInvalido() {
        assertThrows(InvalidEmailException.class, () -> new Email("no-es-un-email"));
        assertThrows(InvalidEmailException.class, () -> new Email(null));
    }
}
