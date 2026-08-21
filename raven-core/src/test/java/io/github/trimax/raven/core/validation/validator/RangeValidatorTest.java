package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Range;

class RangeValidatorTest {

    private final RangeValidator validator = new RangeValidator();

    @Range(min = 1, max = 100)
    @SuppressWarnings("unused")
    private static int rangeField;

    @Test
    void validValueWithinRange() {
        final var result = validator.validate("level", 50, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMinBoundary() {
        final var result = validator.validate("level", 1, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMaxBoundary() {
        final var result = validator.validate("level", 100, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueBelowRange() {
        final var result = validator.validate("level", 0, getAnnotation());
        assertNotNull(result);
        assertEquals("Range", result.constraint());
        assertEquals("level", result.fieldName());
    }

    @Test
    void invalidValueAboveRange() {
        final var result = validator.validate("level", 101, getAnnotation());
        assertNotNull(result);
        assertEquals("Range", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("level", null, getAnnotation()));
    }

    private Range getAnnotation() {
        try {
            return RangeValidatorTest.class.getDeclaredField("rangeField").getAnnotation(Range.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
