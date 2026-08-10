package com.flashdrop.auth.infrastructure.adapter.outbound.security;

import com.flashdrop.auth.application.port.outbound.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) return false;
        return encoder.matches(rawPassword, passwordHash);
    }
}
