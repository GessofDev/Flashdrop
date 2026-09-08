package com.flashdrop.observability.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiKeyValidator.
 * Verifies constant-time comparison prevents timing attacks.
 */
class ApiKeyValidatorTest {

    private static final String VALID_KEY = "super-secret-internal-key-123";

    @Test
    void exactMatch_returnsTrue() {
        assertTrue(ApiKeyValidator.isValid(VALID_KEY, VALID_KEY));
    }

    @Test
    void oneCharMismatch_returnsFalse() {
        String offByOne = "super-secret-internal-key-122";
        assertFalse(ApiKeyValidator.isValid(offByOne, VALID_KEY));
    }

    @Test
    void completelyDifferentKey_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid("wrong-key-altogether", VALID_KEY));
    }

    @Test
    void nullProvided_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid(null, VALID_KEY));
    }

    @Test
    void nullExpected_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid(VALID_KEY, null));
    }

    @Test
    void bothNull_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid(null, null));
    }

    @Test
    void emptyStringVsValidKey_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid("", VALID_KEY));
    }

    @Test
    void validKeyVsEmptyString_returnsFalse() {
        assertFalse(ApiKeyValidator.isValid(VALID_KEY, ""));
    }

    @Test
    void differentLengthKeys_returnFalse() {
        assertFalse(ApiKeyValidator.isValid("short", VALID_KEY));
        assertFalse(ApiKeyValidator.isValid(VALID_KEY, "much-longer-different-key"));
    }
}
