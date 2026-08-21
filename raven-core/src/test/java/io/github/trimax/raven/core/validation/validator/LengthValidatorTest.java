package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Length;

class LengthValidatorTest {

    private final LengthValidator validator = new LengthValidator();

    @Length(min = 3, max = 10)
    @SuppressWarnings("unused")
    private static String lengthField;

    @Test
    void validLengthWithinRange() {
        final var result = validator.validate("name", "hello", getAnnotation());
        assertNull(result);
    }

    @Test
    void validLengthAtMinBoundary() {
        final var result = validator.validate("name", "abc", getAnnotation());
        assertNull(result);
    }

    @Test
    void validLengthAtMaxBoundary() {
        final var result = validator.validate("name", "abcdefghij", getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidLengthTooShort() {
        final var result = validator.validate("name", "ab", getAnnotation());
        assertNotNull(result);
        assertEquals("Length", result.constraint());
        assertEquals("name", result.fieldName());
    }

    @Test
    void invalidLengthTooLong() {
        final var result = validator.validate("name", "abcdefghijk", getAnnotation());
        assertNotNull(result);
        assertEquals("Length", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("name", null, getAnnotation()));
    }

    private Length getAnnotation() {
        try {
            return LengthValidatorTest.class.getDeclaredField("lengthField").getAnnotation(Length.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
