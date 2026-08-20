package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.DecimalRange;

class DecimalRangeValidatorTest {

    private final DecimalRangeValidator validator = new DecimalRangeValidator();

    @SuppressWarnings("unused")
    @DecimalRange(min = 0.0, max = 1.0)
    private static double decimalRangeField;

    @Test
    void validValueWithinRange() {
        final var result = validator.validate("ratio", 0.5, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMinBoundary() {
        final var result = validator.validate("ratio", 0.0, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMaxBoundary() {
        final var result = validator.validate("ratio", 1.0, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueBelowRange() {
        final var result = validator.validate("ratio", -0.1, getAnnotation());
        assertNotNull(result);
        assertEquals("DecimalRange", result.constraint());
        assertEquals("ratio", result.fieldName());
    }

    @Test
    void invalidValueAboveRange() {
        final var result = validator.validate("ratio", 1.1, getAnnotation());
        assertNotNull(result);
        assertEquals("DecimalRange", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("ratio", null, getAnnotation()));
    }

    private DecimalRange getAnnotation() {
        try {
            return DecimalRangeValidatorTest.class.getDeclaredField("decimalRangeField").getAnnotation(DecimalRange.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
