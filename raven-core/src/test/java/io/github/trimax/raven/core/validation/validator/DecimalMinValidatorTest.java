package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.DecimalMin;

class DecimalMinValidatorTest {

    private final DecimalMinValidator validator = new DecimalMinValidator();

    @DecimalMin(1.5)
    @SuppressWarnings("unused")
    private static double decimalMinField;

    @Test
    void validValueAboveMinimum() {
        final var result = validator.validate("price", 2.0, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMinimum() {
        final var result = validator.validate("price", 1.5, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueBelowMinimum() {
        final var result = validator.validate("price", 1.0, getAnnotation());
        assertNotNull(result);
        assertEquals("DecimalMin", result.constraint());
        assertEquals("price", result.fieldName());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("price", null, getAnnotation()));
    }

    private DecimalMin getAnnotation() {
        try {
            return DecimalMinValidatorTest.class.getDeclaredField("decimalMinField").getAnnotation(DecimalMin.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
