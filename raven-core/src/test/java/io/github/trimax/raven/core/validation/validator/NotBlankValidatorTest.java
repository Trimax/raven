package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.NotBlank;

class NotBlankValidatorTest {

    private final NotBlankValidator validator = new NotBlankValidator();

    @NotBlank
    @SuppressWarnings("unused")
    private static String annotatedField;

    @Test
    void validNonBlankString() {
        final var result = validator.validate("name", "hello", getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidNullValue() {
        final var result = validator.validate("name", null, getAnnotation());
        assertNotNull(result);
        assertEquals("NotBlank", result.constraint());
        assertEquals("name", result.fieldName());
    }

    @Test
    void invalidEmptyString() {
        final var result = validator.validate("name", "", getAnnotation());
        assertNotNull(result);
        assertEquals("NotBlank", result.constraint());
    }

    @Test
    void invalidBlankString() {
        final var result = validator.validate("name", "   ", getAnnotation());
        assertNotNull(result);
        assertEquals("NotBlank", result.constraint());
    }

    private NotBlank getAnnotation() {
        try {
            return NotBlankValidatorTest.class.getDeclaredField("annotatedField").getAnnotation(NotBlank.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
