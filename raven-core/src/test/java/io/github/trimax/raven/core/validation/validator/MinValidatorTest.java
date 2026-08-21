package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Min;

class MinValidatorTest {

    private final MinValidator validator = new MinValidator();

    @Min(5)
    @SuppressWarnings("unused")
    private static int minField;

    @Test
    void validValueAboveMinimum() {
        final var result = validator.validate("level", 10, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMinimum() {
        final var result = validator.validate("level", 5, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueBelowMinimum() {
        final var result = validator.validate("level", 3, getAnnotation());
        assertNotNull(result);
        assertEquals("Min", result.constraint());
        assertEquals("level", result.fieldName());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("level", null, getAnnotation()));
    }

    private Min getAnnotation() {
        try {
            return MinValidatorTest.class.getDeclaredField("minField").getAnnotation(Min.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
