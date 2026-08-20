package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Max;

class MaxValidatorTest {

    private final MaxValidator validator = new MaxValidator();

    @Max(100)
    @SuppressWarnings("unused")
    private static int maxField;

    @Test
    void validValueBelowMaximum() {
        final var result = validator.validate("score", 50, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMaximum() {
        final var result = validator.validate("score", 100, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueAboveMaximum() {
        final var result = validator.validate("score", 101, getAnnotation());
        assertNotNull(result);
        assertEquals("Max", result.constraint());
        assertEquals("score", result.fieldName());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("score", null, getAnnotation()));
    }

    private Max getAnnotation() {
        try {
            return MaxValidatorTest.class.getDeclaredField("maxField").getAnnotation(Max.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
