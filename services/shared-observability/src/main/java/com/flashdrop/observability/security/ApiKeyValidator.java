package com.flashdrop.observability.security;

import java.security.MessageDigest;
import java.util.Objects;

/**
 * Constant-time API key validator to prevent timing attacks.
 * Uses {@link MessageDigest#isEqual(byte[], byte[])} which compares
 * all bytes regardless of mismatch position.
 */
public final class ApiKeyValidator {

    private ApiKeyValidator() {}

    /**
     * Compares the provided key against the expected key in constant time.
     * Neither argument may be null.
     *
     * @param provided the key received in the request header
     * @param expected the configured valid key
     * @return true if both are equal, false otherwise
     */
    public static boolean isValid(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] a = provided.getBytes();
        byte[] b = expected.getBytes();
        return MessageDigest.isEqual(a, b);
    }
}
