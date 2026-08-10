package com.flashdrop.auth.domain;

import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.valueobject.Phone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneTest {

    @Test
    void normalizaQuitandoEspacios() {
        assertEquals("+56911111111", new Phone("+56 9 1111 1111").value());
    }

    @Test
    void rechazaTelefonoInvalido() {
        assertThrows(InvalidUserException.class, () -> new Phone("123"));
        assertThrows(InvalidUserException.class, () -> new Phone("no-num"));
    }
}
